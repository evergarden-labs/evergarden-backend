package com.evergarden.evergardenbackend.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** {@code POST /trips}(PLAN-01)의 요청 본문. */
public record TripCreateRequest(
        @NotBlank @Size(min = 1, max = 60) String title,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotEmpty List<String> regionCodes) {
}
