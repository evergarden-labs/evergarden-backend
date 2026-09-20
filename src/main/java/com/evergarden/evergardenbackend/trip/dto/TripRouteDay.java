package com.evergarden.evergardenbackend.trip.dto;

import java.util.List;

/**
 * @param totalDistanceMeters 그 날 좌표가 2개 미만이면 계산할 동선이 없어 {@code null}이다
 */
public record TripRouteDay(short dayNumber, List<RoutePoint> points, Long totalDistanceMeters) {
}
