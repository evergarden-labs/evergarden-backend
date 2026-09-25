package com.evergarden.evergardenbackend.auth.client.dto;

/**
 * 구글 {@code tokeninfo} 응답. 검증에 필요한 두 필드만 받는다(ADR-062).
 *
 * @param sub 사용자 고유 식별자
 * @param aud 토큰이 발급된 클라이언트 ID. 우리 {@code oauth.google.client-id}와 같아야 한다
 */
public record GoogleTokenInfo(String sub, String aud) {
}
