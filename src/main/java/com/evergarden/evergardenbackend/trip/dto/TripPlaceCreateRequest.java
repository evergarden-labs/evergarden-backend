package com.evergarden.evergardenbackend.trip.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /trips/{tripId}/places}(PLAN-02 · PLAN-08)의 요청 본문.
 *
 * @param sortOrder 생략하면 해당 일자의 맨 뒤에 붙는다. 명시했는데 이미 그 자리에
 *                  다른 장소가 있으면, 그 장소부터 뒤쪽을 한 칸씩 밀고 끼워 넣는다
 */
public record TripPlaceCreateRequest(
        @NotNull Long placeId,
        @NotNull @Min(1) Short dayNumber,
        @Min(1) Short sortOrder,
        @Size(max = 500) String memo) {
}
