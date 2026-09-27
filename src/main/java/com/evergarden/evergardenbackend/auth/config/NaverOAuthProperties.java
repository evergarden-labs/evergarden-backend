package com.evergarden.evergardenbackend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code oauth.naver.*} 설정값(ADR-062).
 *
 * @param userInfoUri 프로필 조회 엔드포인트. 검증 전용 엔드포인트가 없어 이걸로 검증을 대신한다
 */
@ConfigurationProperties("oauth.naver")
public record NaverOAuthProperties(String userInfoUri) {
}
