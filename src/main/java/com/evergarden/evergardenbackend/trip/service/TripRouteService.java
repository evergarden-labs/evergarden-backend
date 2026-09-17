package com.evergarden.evergardenbackend.trip.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.trip.dto.BoundingBox;
import com.evergarden.evergardenbackend.trip.dto.RoutePoint;
import com.evergarden.evergardenbackend.trip.dto.TripRoute;
import com.evergarden.evergardenbackend.trip.dto.TripRouteDay;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import com.evergarden.evergardenbackend.trip.repository.TripPlaceRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 코스 시각화용 좌표·동선 데이터 조회(PLAN-10). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripRouteService {

    private final TripRepository tripRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final TripAccessGuard accessGuard;

    public TripRoute getRoute(Long userId, Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
        accessGuard.checkOwner(trip, userId);

        List<TripPlace> tripPlaces = tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip);
        Map<Short, List<TripPlace>> byDay = tripPlaces.stream()
                .collect(Collectors.groupingBy(TripPlace::getDayNumber));

        List<TripRouteDay> days = new ArrayList<>();
        for (short day = 1; day <= trip.durationDays(); day++) {
            List<TripPlace> dayPlaces = byDay.getOrDefault(day, List.of());
            List<RoutePoint> points = dayPlaces.stream().map(this::toPoint).toList();
            Long totalDistance = points.size() < 2 ? null : sumDistance(dayPlaces);
            days.add(new TripRouteDay(day, points, totalDistance));
        }

        return new TripRoute(trip.getId(), days, boundingBox(tripPlaces));
    }

    private RoutePoint toPoint(TripPlace tripPlace) {
        Place place = tripPlace.getPlace();
        return new RoutePoint(tripPlace.getSortOrder(), tripPlace.getId(),
                place.getLat().doubleValue(), place.getLng().doubleValue(), place.getTitle());
    }

    private long sumDistance(List<TripPlace> dayPlaces) {
        long total = 0;
        for (int i = 1; i < dayPlaces.size(); i++) {
            total += GeoDistance.metersBetween(dayPlaces.get(i - 1), dayPlaces.get(i));
        }
        return total;
    }

    private BoundingBox boundingBox(List<TripPlace> tripPlaces) {
        if (tripPlaces.isEmpty()) {
            return null;
        }
        BigDecimal minLat = null;
        BigDecimal minLng = null;
        BigDecimal maxLat = null;
        BigDecimal maxLng = null;
        for (TripPlace tp : tripPlaces) {
            BigDecimal lat = tp.getPlace().getLat();
            BigDecimal lng = tp.getPlace().getLng();
            minLat = minLat == null ? lat : minLat.min(lat);
            minLng = minLng == null ? lng : minLng.min(lng);
            maxLat = maxLat == null ? lat : maxLat.max(lat);
            maxLng = maxLng == null ? lng : maxLng.max(lng);
        }
        return new BoundingBox(minLat.doubleValue(), minLng.doubleValue(), maxLat.doubleValue(), maxLng.doubleValue());
    }
}
