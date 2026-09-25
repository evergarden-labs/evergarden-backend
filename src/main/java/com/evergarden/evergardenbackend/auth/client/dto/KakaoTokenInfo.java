package com.evergarden.evergardenbackend.auth.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 {@code access_token_info} 응답. 검증에 필요한 두 필드만 받는다(ADR-062).
 *
 * @param id    사용자 회원번호(고유 식별자)
 * @param appId 토큰이 발급된 앱 ID. 우리 {@code oauth.kakao.app-id}와 같아야 한다
 */
public record KakaoTokenInfo(Long id, @JsonProperty("app_id") Long appId) {
}
