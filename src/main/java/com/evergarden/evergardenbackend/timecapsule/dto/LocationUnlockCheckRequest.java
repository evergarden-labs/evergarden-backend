package com.evergarden.evergardenbackend.timecapsule.dto;

import jakarta.validation.constraints.NotNull;

/** {@code POST /time-capsules/unlock-check}(TC-04)의 요청 본문. */
public record LocationUnlockCheckRequest(
        @NotNull Double lat,
        @NotNull Double lng) {
}
