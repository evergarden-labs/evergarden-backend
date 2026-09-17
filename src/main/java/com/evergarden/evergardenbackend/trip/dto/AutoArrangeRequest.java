package com.evergarden.evergardenbackend.trip.dto;

import java.util.List;

/**
 * {@code POST /trips/{tripId}/auto-arrange}(PLAN-09)의 요청 본문. 본문 없이 호출하면
 * 일정에 이미 담긴 장소만 재배치한다.
 *
 * @param additionalPlaceIds 아직 구현하지 않았다 — 저장된 적 없는 장소는 {@code tripPlaceId}가
 *                            없어 응답을 그대로 {@code PUT /places/order}에 넘길 수 없다는 명세
 *                            모순이 있다. {@code docs/decisions.md}의 "플래너 도메인에서 새로
 *                            드러난 미정 항목 (2026-09-18)" 참고. 값을 보내도 무시된다
 */
public record AutoArrangeRequest(List<Long> additionalPlaceIds) {
}
