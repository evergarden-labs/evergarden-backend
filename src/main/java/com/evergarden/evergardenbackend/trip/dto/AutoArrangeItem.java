package com.evergarden.evergardenbackend.trip.dto;

import com.evergarden.evergardenbackend.place.dto.PlaceSummary;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * {@code autoArrangeTrip} 응답과 {@code applyAutoArrangement} 요청이 함께 쓰는 모양(ADR-060).
 * 응답으로 나올 때는 서버가 채우고, 요청으로 들어올 때는 그 응답을 그대로 다시 보낸 것이다.
 *
 * @param tripPlaceId 이미 일정에 저장된 장소면 그 식별자. {@code additionalPlaceIds}로
 *                    넣어봐서 아직 저장되지 않은 장소면 {@code null}이다 — 적용({@code apply})
 *                    시점에 {@code place.placeId()}로 새로 추가된다
 */
public record AutoArrangeItem(
        Long tripPlaceId,
        @Min(1) short dayNumber,
        @Min(1) short sortOrder,
        @NotNull PlaceSummary place) {
}
