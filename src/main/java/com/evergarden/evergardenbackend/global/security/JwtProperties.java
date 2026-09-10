package com.evergarden.evergardenbackend.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code jwt.*} 설정값.
 *
 * @param secretKey                    HS256 서명 키. 256비트(32자) 이상이어야 한다
 * @param accessTokenExpirationMinutes 액세스 토큰 만료 (명세 · ADR-010)
 * @param refreshTokenExpirationDays   리프레시 토큰 만료 (명세 · ADR-010)
 */
@ConfigurationProperties("jwt")
public record JwtProperties(
        String secretKey,
        long accessTokenExpirationMinutes,
        long refreshTokenExpirationDays) {
}
