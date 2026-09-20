package com.evergarden.evergardenbackend.trip.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.dto.PlaceSummary;
import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.place.repository.PlaceRepository;
import com.evergarden.evergardenbackend.trip.dto.AutoArrangeApplyRequest;
import com.evergarden.evergardenbackend.trip.dto.AutoArrangeItem;
import com.evergarden.evergardenbackend.trip.dto.AutoArrangeRequest;
import com.evergarden.evergardenbackend.trip.dto.AutoArrangeResult;
import com.evergarden.evergardenbackend.trip.dto.TripDetail;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import com.evergarden.evergardenbackend.trip.repository.TripPlaceRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일정에 담긴 장소들의 순서를 이동 거리가 짧아지도록 재배치한 제안을 만들고(PLAN-09),
 * 그 제안을 실제로 적용한다(ADR-060). 날짜는 그대로 두고 하루 안에서만 순서를 바꾼다 —
 * 날짜를 옮기는 건 이 오퍼레이션의 범위가 아니다(ADR-020, 장소 추천이 아니라 순서
 * 정리까지만). 단, {@code additionalPlaceIds}로 넣어본 새 장소는 어느 날짜가 좋을지까지
 * 서버가 고른다 — 아직 어느 날에도 속해 있지 않으니 정해줄 수밖에 없다.
 */
@Service
@RequiredArgsConstructor
public class TripAutoArrangeService {

    /**
     * 재배치 적용({@link #apply})에서 기존 장소들을 잠깐 옮겨 두는 임시 순서값의 시작점.
     * {@code TripPlaceService}의 같은 용도 상수와 이유가 같다 — {@code sort_order}는
     * DB에 {@code CHECK (>= 1)}이 걸려 있어 음수를 못 쓴다.
     */
    private static final short SCRATCH_ORDER_BASE = 30000;

    private final TripRepository tripRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final PlaceRepository placeRepository;
    private final TripAccessGuard accessGuard;
    private final TripService tripService;

    @Transactional(readOnly = true)
    public AutoArrangeResult propose(Long userId, Long tripId, AutoArrangeRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        accessGuard.checkOwner(trip, userId);

        List<TripPlace> tripPlaces = tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip);
        List<Long> additionalPlaceIds = request != null && request.additionalPlaceIds() != null
                ? request.additionalPlaceIds() : List.of();
        if (tripPlaces.isEmpty() && additionalPlaceIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Map<Short, List<TripPlace>> byDay = new TreeMap<>();
        for (short day = 1; day <= trip.durationDays(); day++) {
            byDay.put(day, new ArrayList<>());
        }
        for (TripPlace tripPlace : tripPlaces) {
            byDay.get(tripPlace.getDayNumber()).add(tripPlace);
        }

        // additionalPlaceIds는 아직 저장 전이라 "지금" 거리에는 안 들어간다
        long currentDistance = byDay.values().stream().mapToLong(this::sumDistance).sum();

        for (Long placeId : additionalPlaceIds) {
            Place place = placeRepository.findById(placeId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
            short bestDay = cheapestDay(trip, byDay, place);
            byDay.get(bestDay).add(TripPlace.builder().trip(trip).place(place)
                    .dayNumber(bestDay).sortOrder((short) 1).build());
        }

        long proposedDistance = 0;
        List<AutoArrangeItem> items = new ArrayList<>();
        for (Map.Entry<Short, List<TripPlace>> entry : byDay.entrySet()) {
            List<TripPlace> pool = entry.getValue();
            if (pool.isEmpty()) {
                continue;
            }
            short day = entry.getKey();
            List<TripPlace> optimized = RouteOptimizer.optimize(pool);
            proposedDistance += sumDistance(optimized);

            short order = 1;
            for (TripPlace tripPlace : optimized) {
                items.add(new AutoArrangeItem(tripPlace.getId(), day, order++, PlaceSummary.of(tripPlace.getPlace())));
            }
        }

        return new AutoArrangeResult(items, currentDistance, proposedDistance);
    }

    /**
     * {@code autoArrangeTrip} 응답을 그대로 받아 적용한다(ADR-060). {@code tripPlaceId}가
     * 있으면 자리 이동, 없으면({@code additionalPlaceIds}로 넣어본 것) 그 자리에 새로 추가한다.
     */
    @Transactional
    public TripDetail apply(Long userId, Long tripId, AutoArrangeApplyRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        accessGuard.checkOwner(trip, userId);

        List<TripPlace> current = tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip);
        Map<Long, TripPlace> byId = current.stream().collect(Collectors.toMap(TripPlace::getId, tp -> tp));

        List<AutoArrangeItem> existingItems = new ArrayList<>();
        List<AutoArrangeItem> newItems = new ArrayList<>();
        Set<Long> seenTripPlaceIds = new HashSet<>();
        Set<String> seenSlots = new HashSet<>();
        for (AutoArrangeItem item : request.items()) {
            validateDayNumber(trip, item.dayNumber());
            if (!seenSlots.add(item.dayNumber() + "-" + item.sortOrder())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            if (item.tripPlaceId() != null) {
                if (!byId.containsKey(item.tripPlaceId()) || !seenTripPlaceIds.add(item.tripPlaceId())) {
                    throw new BusinessException(ErrorCode.TRIP_PLACE_NOT_FOUND);
                }
                existingItems.add(item);
            } else {
                newItems.add(item);
            }
        }
        if (seenTripPlaceIds.size() != byId.size()) {
            // 부분 전송 거부 — 기존 장소 중 빠진 게 있다(TripPlaceService.replaceOrder와 같은 이유)
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        // 1단계: 기존 항목 전부를 서로 안 겹치는 임시 자리로 — 최종 자리를 채울 때 기존 값과 안 부딪히게
        short temp = SCRATCH_ORDER_BASE;
        for (TripPlace tripPlace : current) {
            tripPlace.relocate(tripPlace.getDayNumber(), temp++);
        }
        tripPlaceRepository.flush();

        // 2단계: 기존 항목을 최종 자리로
        for (AutoArrangeItem item : existingItems) {
            byId.get(item.tripPlaceId()).relocate(item.dayNumber(), item.sortOrder());
        }
        tripPlaceRepository.flush();

        // 3단계: additionalPlaceIds로 넣어봤던 항목을 실제로 새로 추가
        List<TripPlace> created = new ArrayList<>();
        for (AutoArrangeItem item : newItems) {
            Place place = placeRepository.findById(item.place().placeId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
            created.add(TripPlace.builder().trip(trip).place(place)
                    .dayNumber(item.dayNumber()).sortOrder(item.sortOrder()).build());
        }
        tripPlaceRepository.saveAll(created);

        return tripService.toDetail(trip);
    }

    private long sumDistance(List<TripPlace> dayPlaces) {
        long total = 0;
        for (int i = 1; i < dayPlaces.size(); i++) {
            total += GeoDistance.metersBetween(dayPlaces.get(i - 1), dayPlaces.get(i));
        }
        return total;
    }

    /** 그 날짜에 이 장소를 끼워 넣었을 때 늘어나는 최소 거리(최적 삽입 비용)가 가장 적은 날짜. */
    private short cheapestDay(Trip trip, Map<Short, List<TripPlace>> byDay, Place candidate) {
        short bestDay = 1;
        long bestCost = Long.MAX_VALUE;
        for (short day = 1; day <= trip.durationDays(); day++) {
            long cost = insertionCost(byDay.get(day), candidate);
            if (cost < bestCost) {
                bestCost = cost;
                bestDay = day;
            }
        }
        return bestDay;
    }

    private long insertionCost(List<TripPlace> pool, Place candidate) {
        if (pool.isEmpty()) {
            return 0;
        }
        if (pool.size() == 1) {
            return GeoDistance.metersBetween(pool.get(0).getPlace(), candidate);
        }
        long best = Math.min(
                GeoDistance.metersBetween(pool.get(0).getPlace(), candidate),
                GeoDistance.metersBetween(pool.get(pool.size() - 1).getPlace(), candidate));
        for (int i = 1; i < pool.size(); i++) {
            Place a = pool.get(i - 1).getPlace();
            Place b = pool.get(i).getPlace();
            long delta = GeoDistance.metersBetween(a, candidate) + GeoDistance.metersBetween(candidate, b)
                    - GeoDistance.metersBetween(a, b);
            best = Math.min(best, delta);
        }
        return best;
    }

    private void validateDayNumber(Trip trip, short dayNumber) {
        if (dayNumber < 1 || dayNumber > trip.durationDays()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
