package com.evergarden.evergardenbackend.trip.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.dto.PlaceSummary;
import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.place.repository.PlaceRepository;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import com.evergarden.evergardenbackend.trip.repository.TripPlaceRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일정에 담긴 장소 주변의 관광지를 추천한다(PLAN-08). 기준 일자(생략하면 일정 전체)에
 * 담긴 장소들 각각의 반경 5km 안에서 찾고, 이미 일정에 담긴 장소는 뺀다(ADR-042).
 *
 * <p>이미 전국 데이터를 동기화해 둔 {@code places} 테이블에서만 찾는다 — 라이브로
 * TourAPI를 다시 부르지 않는다(searchPlaces와 같은 이유).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripNearbyPlaceService {

    private static final int RADIUS_METERS = 5_000;
    private static final double METERS_PER_LAT_DEGREE = 111_320;

    private final TripRepository tripRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final PlaceRepository placeRepository;
    private final TripAccessGuard accessGuard;

    public Page<PlaceSummary> listNearby(Long userId, Long tripId, Short dayNumber, Pageable pageable) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        accessGuard.checkOwner(trip, userId);

        List<TripPlace> allTripPlaces = tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip);
        List<TripPlace> reference = dayNumber == null
                ? allTripPlaces
                : allTripPlaces.stream().filter(tp -> tp.getDayNumber() == dayNumber).toList();

        if (reference.isEmpty()) {
            return Page.empty(pageable);
        }

        Set<Long> alreadyInTrip = allTripPlaces.stream()
                .map(tp -> tp.getPlace().getId())
                .collect(Collectors.toSet());

        Set<Place> candidates = new LinkedHashSet<>();
        for (TripPlace ref : reference) {
            candidates.addAll(placesWithinBoundingBox(ref.getPlace()));
        }

        List<PlaceSummary> nearby = candidates.stream()
                .filter(place -> !alreadyInTrip.contains(place.getId()))
                .filter(place -> withinRadiusOfAny(place, reference))
                .sorted(Comparator.comparingLong(place -> nearestDistance(place, reference)))
                .map(PlaceSummary::of)
                .toList();

        return paginate(nearby, pageable);
    }

    private List<Place> placesWithinBoundingBox(Place center) {
        double latDelta = RADIUS_METERS / METERS_PER_LAT_DEGREE;
        double lngDelta = RADIUS_METERS / (METERS_PER_LAT_DEGREE * Math.cos(Math.toRadians(center.getLat().doubleValue())));

        BigDecimal lat = center.getLat();
        BigDecimal lng = center.getLng();
        return placeRepository.findByLatBetweenAndLngBetween(
                lat.subtract(BigDecimal.valueOf(latDelta)), lat.add(BigDecimal.valueOf(latDelta)),
                lng.subtract(BigDecimal.valueOf(lngDelta)), lng.add(BigDecimal.valueOf(lngDelta)));
    }

    private boolean withinRadiusOfAny(Place place, List<TripPlace> reference) {
        return reference.stream().anyMatch(ref -> distance(ref.getPlace(), place) <= RADIUS_METERS);
    }

    private long nearestDistance(Place place, List<TripPlace> reference) {
        return reference.stream().mapToLong(ref -> distance(ref.getPlace(), place)).min().orElse(Long.MAX_VALUE);
    }

    private long distance(Place a, Place b) {
        return GeoDistance.metersBetween(a.getLat(), a.getLng(), b.getLat(), b.getLng());
    }

    private Page<PlaceSummary> paginate(List<PlaceSummary> all, Pageable pageable) {
        int from = Math.min((int) pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
    }
}
