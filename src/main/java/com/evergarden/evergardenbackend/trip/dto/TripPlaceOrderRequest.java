package com.evergarden.evergardenbackend.trip.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * {@code PUT /trips/{tripId}/places/order}(PLAN-02 · PLAN-04)의 요청 본문.
 * 일정에 담긴 모든 장소를 보내야 한다 — 부분 전송은 거부된다.
 */
public record TripPlaceOrderRequest(@NotEmpty @Valid List<Item> items) {

    public record Item(
            @NotNull Long tripPlaceId,
            @NotNull @Min(1) Short dayNumber,
            @NotNull @Min(1) Short sortOrder) {
    }
}
