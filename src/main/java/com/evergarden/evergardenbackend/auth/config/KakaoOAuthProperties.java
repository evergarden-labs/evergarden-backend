package com.evergarden.evergardenbackend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code oauth.kakao.*} 설정값(ADR-062).
 *
 * @param tokenInfoUri 토큰 검증 엔드포인트. {@code Authorization: Bearer} 헤더로 부른다
 * @param appId        우리 앱의 카카오 앱 ID. 응답의 {@code app_id}와 비교한다
 */
@ConfigurationProperties("oauth.kakao")
public record KakaoOAuthProperties(String tokenInfoUri, String appId) {
}
