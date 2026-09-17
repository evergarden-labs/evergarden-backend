package com.evergarden.evergardenbackend.trip.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** {@code PATCH /trips/{tripId}/places/{tripPlaceId}}(PLAN-04)의 요청 본문. 보내지 않은 필드는 그대로 둔다. */
public record TripPlaceUpdateRequest(
        @Min(1) Short dayNumber,
        @Min(1) Short sortOrder,
        @Size(max = 500) String memo) {

    public boolean isEmpty() {
        return dayNumber == null && sortOrder == null && memo == null;
    }
}
