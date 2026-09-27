package com.evergarden.evergardenbackend.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code refreshToken}(AUTH-05) 요청 본문. */
public record RefreshTokenRequest(@NotBlank String refreshToken) {
}
