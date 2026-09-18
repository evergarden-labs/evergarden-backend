package com.evergarden.evergardenbackend.trip.dto;

import java.util.List;

/**
 * {@code GET /trips/{tripId}/route}(PLAN-10)의 응답. 일정 상세와 달리 지도를 그리는
 * 데만 필요한 값(좌표·거리)만 담는다 — 장소 설명·메모까지 받는 건 낭비라 조회를 나눴다.
 *
 * @param boundingBox 일정에 담긴 장소가 하나도 없으면 맞출 범위가 없어 {@code null}이다
 */
public record TripRoute(Long tripId, List<TripRouteDay> days, BoundingBox boundingBox) {
}
