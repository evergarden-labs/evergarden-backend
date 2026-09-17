package com.evergarden.evergardenbackend.trip.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.dto.PlaceSummary;
import com.evergarden.evergardenbackend.trip.dto.AutoArrangeItem;
import com.evergarden.evergardenbackend.trip.dto.AutoArrangeRequest;
import com.evergarden.evergardenbackend.trip.dto.AutoArrangeResult;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import com.evergarden.evergardenbackend.trip.repository.TripPlaceRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일정에 담긴 장소들의 순서를 이동 거리가 짧아지도록 재배치한 제안을 만든다(PLAN-09).
 * 날짜는 그대로 두고 하루 안에서만 순서를 바꾼다 — 날짜를 옮기는 건 이 오퍼레이션의
 * 범위가 아니다(ADR-020, 장소 추천이 아니라 순서 정리까지만).
 *
 * <p>제안만 하고 저장하지 않는다(ADR-031) — 순수 계산이라 읽기 전용 트랜잭션으로 충분하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripAutoArrangeService {

    private final TripRepository tripRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final TripAccessGuard accessGuard;

    public AutoArrangeResult propose(Long userId, Long tripId, AutoArrangeRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        accessGuard.checkOwner(trip, userId);

        List<TripPlace> tripPlaces = tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip);
        if (tripPlaces.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Map<Short, List<TripPlace>> byDay = tripPlaces.stream()
                .collect(Collectors.groupingBy(TripPlace::getDayNumber, TreeMap::new, Collectors.toList()));

        long currentDistance = 0;
        long proposedDistance = 0;
        List<AutoArrangeItem> items = new ArrayList<>();

        for (Map.Entry<Short, List<TripPlace>> entry : byDay.entrySet()) {
            short day = entry.getKey();
            List<TripPlace> current = entry.getValue();
            currentDistance += sumDistance(current);

            List<TripPlace> optimized = RouteOptimizer.optimize(current);
            proposedDistance += sumDistance(optimized);

            short order = 1;
            for (TripPlace tripPlace : optimized) {
                items.add(new AutoArrangeItem(tripPlace.getId(), day, order++, PlaceSummary.of(tripPlace.getPlace())));
            }
        }

        return new AutoArrangeResult(items, currentDistance, proposedDistance);
    }

    private long sumDistance(List<TripPlace> dayPlaces) {
        long total = 0;
        for (int i = 1; i < dayPlaces.size(); i++) {
            total += GeoDistance.metersBetween(dayPlaces.get(i - 1), dayPlaces.get(i));
        }
        return total;
    }
}
