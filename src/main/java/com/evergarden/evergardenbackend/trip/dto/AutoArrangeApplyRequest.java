package com.evergarden.evergardenbackend.trip.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * {@code POST /trips/{tripId}/auto-arrange/apply}(PLAN-09)의 요청 본문(ADR-060).
 * {@code autoArrangeTrip} 응답을 그대로 다시 보내는 것과 같은 모양이라 재가공이 필요 없다.
 */
public record AutoArrangeApplyRequest(@NotEmpty @Valid List<AutoArrangeItem> items) {
}
