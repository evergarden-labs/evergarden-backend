package com.evergarden.evergardenbackend.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code loginWithSocial}(AUTH-01·02) 요청 본문. */
public record SocialLoginRequest(@NotBlank String socialAccessToken) {
}
