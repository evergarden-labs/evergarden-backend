package com.evergarden.evergardenbackend.place.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code kakao-local.*} 설정값. {@code oauth.kakao}(로그인 토큰 검증)와는 목적이 달라
 * 별도 REST API 키를 쓴다 — TourAPI 법정동코드엔 좌표가 없어, 지역명을 좌표로
 * 바꾸는 데만 쓴다({@code docs/place-data-sync.md} 3절).
 *
 * @param baseUrl    카카오 로컬 API 베이스 URL
 * @param restApiKey 카카오 디벨로퍼스 앱의 REST API 키. {@code Authorization} 헤더로 보낸다
 */
@ConfigurationProperties("kakao-local")
public record KakaoLocalProperties(String baseUrl, String restApiKey) {
}
