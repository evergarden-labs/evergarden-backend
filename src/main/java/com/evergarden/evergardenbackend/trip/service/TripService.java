package com.evergarden.evergardenbackend.trip.service;

import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import com.evergarden.evergardenbackend.trip.dto.TripCreateRequest;
import com.evergarden.evergardenbackend.trip.dto.TripDetail;
import com.evergarden.evergardenbackend.trip.dto.TripSummary;
import com.evergarden.evergardenbackend.trip.dto.TripUpdateRequest;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import com.evergarden.evergardenbackend.trip.entity.TripRegion;
import com.evergarden.evergardenbackend.trip.entity.TripStatus;
import com.evergarden.evergardenbackend.trip.repository.TripPlaceRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRegionRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 여행 일정 기본 CRUD(PLAN-01·03·04·05·11). */
@Service
@RequiredArgsConstructor
@Transactional
public class TripService {

    private final TripRepository tripRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final TripRegionRepository tripRegionRepository;
    private final RegionRepository regionRepository;
    private final ArchiveRepository archiveRepository;
    private final UserRepository userRepository;
    private final TripAccessGuard accessGuard;
    private final TripMapper tripMapper;

    public TripDetail create(Long userId, TripCreateRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }
        Trip trip = Trip.builder()
                .owner(userRepository.getReferenceById(userId))
                .title(request.title())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();
        tripRepository.save(trip);
        linkRegions(trip, request.regionCodes());
        return toDetail(trip);
    }

    public TripDetail get(Long userId, Long tripId) {
        Trip trip = findTrip(tripId);
        accessGuard.checkOwner(trip, userId);
        return toDetail(trip);
    }

    /**
     * 기간을 줄여 갈 곳 없는 장소가 생기면 거부한다(ADR-041) — 서버가 말없이
     * 지우거나 옮기지 않고, 사용자가 먼저 정리하게 한다.
     */
    public TripDetail update(Long userId, Long tripId, TripUpdateRequest request) {
        if (request.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Trip trip = findTrip(tripId);
        accessGuard.checkOwner(trip, userId);

        LocalDate newStart = request.startDate() != null ? request.startDate() : trip.getStartDate();
        LocalDate newEnd = request.endDate() != null ? request.endDate() : trip.getEndDate();
        if (newStart.isAfter(newEnd)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }

        if (request.startDate() != null || request.endDate() != null) {
            rejectIfPlacesDisplaced(trip, newStart, newEnd);
            trip.updatePeriod(newStart, newEnd);
        }
        if (request.title() != null) {
            trip.updateTitle(request.title());
        }
        if (request.regionCodes() != null) {
            tripRegionRepository.deleteAll(tripRegionRepository.findByTrip(trip));
            linkRegions(trip, request.regionCodes());
        }
        return toDetail(trip);
    }

    public void delete(Long userId, Long tripId) {
        Trip trip = findTrip(tripId);
        accessGuard.checkOwner(trip, userId);
        tripRepository.delete(trip);
    }

    /**
     * 예정된 여행(UPCOMING·ONGOING)이 먼저, 그 안에서 시작일 오름차순.
     * 지난 여행(PAST)은 그 아래에 시작일 내림차순(ADR-040).
     *
     * <p>상태가 저장된 컬럼이 아니라 매번 계산돼서 DB 정렬로 표현하기 까다롭다.
     * 한 사용자의 일정 수는 개인 플래너 규모라 전부 메모리에 올려 정렬한다 —
     * 사용자당 일정이 아주 많아지면 그때 다시 봐야 한다.
     */
    public Page<TripSummary> list(Long userId, Pageable pageable) {
        LocalDate today = LocalDate.now();
        List<Trip> sorted = tripRepository.findByOwner_Id(userId).stream()
                .sorted((a, b) -> compare(a, b, today))
                .toList();

        int total = sorted.size();
        int from = Math.min((int) pageable.getOffset(), total);
        int to = Math.min(from + pageable.getPageSize(), total);
        List<TripSummary> content = sorted.subList(from, to).stream()
                .map(this::toSummary)
                .toList();
        return new PageImpl<>(content, pageable, total);
    }

    private int compare(Trip a, Trip b, LocalDate today) {
        boolean aPast = TripStatus.of(a.getStartDate(), a.getEndDate(), today) == TripStatus.PAST;
        boolean bPast = TripStatus.of(b.getStartDate(), b.getEndDate(), today) == TripStatus.PAST;
        if (aPast != bPast) {
            return aPast ? 1 : -1;
        }
        return aPast ? b.getStartDate().compareTo(a.getStartDate()) : a.getStartDate().compareTo(b.getStartDate());
    }

    private void rejectIfPlacesDisplaced(Trip trip, LocalDate newStart, LocalDate newEnd) {
        int newDuration = (int) ChronoUnit.DAYS.between(newStart, newEnd) + 1;
        Map<Integer, Long> displacedByDay = tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)
                .stream()
                .filter(tp -> tp.getDayNumber() > newDuration)
                .collect(Collectors.groupingBy(tp -> (int) tp.getDayNumber(), LinkedHashMap::new, Collectors.counting()));
        if (!displacedByDay.isEmpty()) {
            List<Map<String, Object>> displacedDays = displacedByDay.entrySet().stream()
                    .<Map<String, Object>>map(e -> Map.of("dayNumber", e.getKey(), "placeCount", e.getValue()))
                    .toList();
            throw new BusinessException(ErrorCode.INVALID_REQUEST, Map.of("displacedDays", displacedDays));
        }
    }

    private void linkRegions(Trip trip, List<String> regionCodes) {
        for (String code : regionCodes) {
            Region region = regionRepository.findById(code)
                    .orElseThrow(() -> new BusinessException(ErrorCode.REGION_NOT_FOUND));
            tripRegionRepository.save(new TripRegion(trip, region));
        }
    }

    private Trip findTrip(Long tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
    }

    private TripSummary toSummary(Trip trip) {
        List<Region> regions = regions(trip);
        List<TripPlace> tripPlaces = tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip);
        return tripMapper.toSummary(trip, regions, tripPlaces, linkedArchiveId(trip));
    }

    private TripDetail toDetail(Trip trip) {
        List<Region> regions = regions(trip);
        List<TripPlace> tripPlaces = tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip);
        return tripMapper.toDetail(trip, regions, tripPlaces, linkedArchiveId(trip));
    }

    private List<Region> regions(Trip trip) {
        return tripRegionRepository.findByTrip(trip).stream().map(TripRegion::getRegion).toList();
    }

    private Long linkedArchiveId(Trip trip) {
        return archiveRepository.findByTrip_Id(trip.getId())
                .map(Archive::getId)
                .orElse(null);
    }
}
