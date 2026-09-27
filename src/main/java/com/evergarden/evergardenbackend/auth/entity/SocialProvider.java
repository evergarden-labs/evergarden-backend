package com.evergarden.evergardenbackend.auth.entity;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;

/**
 * 소셜 로그인 제공자.
 *
 * <p>앱이 SDK로 받은 토큰을 서버가 제공자 API로 검증한다(ADR-010).
 * 서버가 로그인 페이지로 리다이렉트하지 않으므로 인가 코드 교환이 없다.
 */
public enum SocialProvider {
    GOOGLE,
    KAKAO,
    NAVER;

    /**
     * 경로 변수({@code {provider}})로 받은 소문자 값을 파싱한다. 명세의 enum이 소문자
     * ({@code google}·{@code kakao}·{@code naver})라 이 이름 그대로는 못 쓴다 — 대문자
     * 상수({@code GOOGLE})로 직접 바인딩하면 정상 요청까지 타입 불일치로 막힌다.
     */
    public static SocialProvider from(String rawValue) {
        if (rawValue == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        try {
            return SocialProvider.valueOf(rawValue.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
