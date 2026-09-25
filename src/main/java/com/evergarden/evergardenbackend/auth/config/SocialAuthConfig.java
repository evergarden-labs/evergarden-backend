package com.evergarden.evergardenbackend.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 소셜 로그인 토큰 검증에 쓸 {@code RestClient} 설정(ADR-010 · ADR-062).
 *
 * <p>{@code ExternalApiConfig}와 달리 baseUrl을 두지 않는다 — 제공자별 URI
 * ({@code oauth.*.token-info-uri} 등)가 이미 완전한 절대 경로로 설정돼 있고,
 * 제공자가 셋이라 baseUrl 하나로 묶을 수 없다.
 */
@Configuration
@EnableConfigurationProperties({
        GoogleOAuthProperties.class,
        KakaoOAuthProperties.class,
        NaverOAuthProperties.class})
public class SocialAuthConfig {

    @Bean
    public RestClient socialAuthRestClient() {
        return RestClient.create();
    }
}
