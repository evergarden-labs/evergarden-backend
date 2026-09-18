package com.evergarden.evergardenbackend.trip.dto;

import java.util.List;

/**
 * {@code POST /trips/{tripId}/auto-arrange}(PLAN-09)의 요청 본문. 본문 없이 호출하면
 * 일정에 이미 담긴 장소만 재배치한다.
 *
 * @param additionalPlaceIds 아직 일정에 없지만 이번 배치에 함께 넣어보고 싶은 장소들(ADR-060).
 *                           응답 {@code items}에 {@code tripPlaceId: null}로 나타난다.
 *                           어느 날짜에 넣을지는 그 장소를 넣었을 때 이동 거리가 가장 적게
 *                           늘어나는 날짜로 서버가 고른다
 */
public record AutoArrangeRequest(List<Long> additionalPlaceIds) {
}
