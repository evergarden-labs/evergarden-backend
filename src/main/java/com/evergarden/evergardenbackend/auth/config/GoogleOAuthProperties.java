package com.evergarden.evergardenbackend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code oauth.google.*} 설정값(ADR-062).
 *
 * @param tokenInfoUri 토큰 검증 엔드포인트. 쿼리 파라미터로 {@code access_token}을 실어 부른다
 * @param clientId     우리 앱의 구글 클라이언트 ID. 응답의 {@code aud}와 비교한다
 */
@ConfigurationProperties("oauth.google")
public record GoogleOAuthProperties(String tokenInfoUri, String clientId) {
}
