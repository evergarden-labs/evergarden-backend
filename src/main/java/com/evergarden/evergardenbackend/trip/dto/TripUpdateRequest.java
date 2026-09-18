package com.evergarden.evergardenbackend.trip.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** {@code PATCH /trips/{tripId}}(PLAN-04)의 요청 본문. 보내지 않은 필드는 그대로 둔다. */
public record TripUpdateRequest(
        @Size(min = 1, max = 60) String title,
        LocalDate startDate,
        LocalDate endDate,
        List<String> regionCodes) {

    public boolean isEmpty() {
        return title == null && startDate == null && endDate == null && regionCodes == null;
    }
}
