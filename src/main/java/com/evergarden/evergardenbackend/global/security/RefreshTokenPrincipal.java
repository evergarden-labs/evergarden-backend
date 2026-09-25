package com.evergarden.evergardenbackend.global.security;

/**
 * 리프레시 토큰을 검증하고 꺼낸 값.
 *
 * <p>{@link AuthPrincipal}과 따로 두는 이유 — 리프레시 토큰에만 있는 {@code jti}가 필요하다.
 * {@code POST /auth/token/refresh}가 이 값으로 {@code refresh:jti:{jti}}를 조회·삭제한다(ADR-055).
 *
 * @param userId {@code users.id}
 * @param role   재발급할 액세스 토큰에 그대로 실을 역할
 * @param jti    Redis 키로 쓰는 고유 ID
 */
public record RefreshTokenPrincipal(Long userId, Role role, String jti) {
}
