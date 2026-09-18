package com.evergarden.evergardenbackend.place.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code tour-api.*} 설정값(ADR-004).
 *
 * @param baseUrl    한국관광공사 국문 관광정보 서비스(KorService2) 베이스 URL
 * @param serviceKey 공공데이터포털에서 발급받은 인증키(Decoding). 쿼리 파라미터로 실어 보낸다
 */
@ConfigurationProperties("tour-api")
public record TourApiProperties(String baseUrl, String serviceKey) {
}
