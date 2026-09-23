package com.evergarden.evergardenbackend.map.dto;

import jakarta.validation.constraints.NotNull;

/** {@code POST /region-visits}(MAP-01)의 요청 본문. */
public record RegionVisitRequest(
        @NotNull Double lat,
        @NotNull Double lng,
        @NotNull Double accuracyMeters,
        String regionCode) {
}
