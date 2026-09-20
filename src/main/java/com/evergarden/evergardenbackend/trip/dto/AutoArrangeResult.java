package com.evergarden.evergardenbackend.trip.dto;

import java.util.List;

/**
 * {@code POST /trips/{tripId}/auto-arrange}(PLAN-09)의 응답. 제안일 뿐 저장되지 않는다(ADR-031).
 * 그대로 {@code POST /trips/{tripId}/auto-arrange/apply}에 다시 보내면 적용된다(ADR-060).
 *
 * @param currentDistanceMeters  지금 저장된 순서대로 다녔을 때의 이동 거리 합.
 *                               {@code additionalPlaceIds}는 아직 저장 전이라 포함되지 않는다
 * @param proposedDistanceMeters 제안한 순서로 다녔을 때의 이동 거리 합. {@code additionalPlaceIds}도 포함된다
 */
public record AutoArrangeResult(List<AutoArrangeItem> items, long currentDistanceMeters, long proposedDistanceMeters) {
}
