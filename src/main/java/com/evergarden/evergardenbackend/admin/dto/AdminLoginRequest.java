package com.evergarden.evergardenbackend.admin.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code adminLogin}(ADMIN-01) 요청 본문. */
public record AdminLoginRequest(@NotBlank String loginId, @NotBlank String password) {
}
