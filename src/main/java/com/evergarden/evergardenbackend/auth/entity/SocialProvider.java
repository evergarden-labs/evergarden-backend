package com.evergarden.evergardenbackend.auth.entity;

/**
 * 소셜 로그인 제공자.
 *
 * <p>앱이 SDK로 받은 토큰을 서버가 제공자 API로 검증한다(ADR-010).
 * 서버가 로그인 페이지로 리다이렉트하지 않으므로 인가 코드 교환이 없다.
 */
public enum SocialProvider {
    GOOGLE,
    KAKAO,
    NAVER
}
