package com.evergarden.evergardenbackend.auth.dto;

/**
 * 명세의 {@code AuthResult} 스키마({@code TokenPair}를 {@code allOf}로 담은 평탄화 버전).
 * {@code loginWithSocial}(AUTH-01·02)·{@code restoreAccount}(AUTH-07)가 쓴다.
 */
public record AuthResult(
        String accessToken,
        String refreshToken,
        boolean isNewUser,
        boolean onboardingCompleted) {
}
