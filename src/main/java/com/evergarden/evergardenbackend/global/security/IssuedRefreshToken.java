package com.evergarden.evergardenbackend.global.security;

/**
 * 새로 발급한 리프레시 토큰과 그 {@code jti}.
 *
 * <p>Redis에 {@code refresh:jti:{jti}} → {@code userId}를 쓰려면 발급한 토큰 문자열이 아니라
 * {@code jti}가 필요하다(ADR-055). 발급 직후 다시 파싱하지 않도록 여기서 함께 돌려준다.
 *
 * @param token 클라이언트에 내려줄 리프레시 토큰 문자열
 * @param jti   토큰에 심은 고유 ID. Redis 키로 쓴다
 */
public record IssuedRefreshToken(String token, String jti) {
}
