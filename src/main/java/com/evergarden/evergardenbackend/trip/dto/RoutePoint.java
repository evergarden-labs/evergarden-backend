package com.evergarden.evergardenbackend.trip.dto;

/** {@code GET /trips/{tripId}/route}(PLAN-10)의 좌표 한 점. */
public record RoutePoint(short sortOrder, Long tripPlaceId, double lat, double lng, String title) {
}
