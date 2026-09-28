package com.evergarden.evergardenbackend.admin.dto;

/**
 * 명세의 {@code AdminSession} 스키마. {@code refreshToken}은 항상 {@code null}이다 —
 * 관리자 로그인은 리프레시 토큰을 발급하지 않는다. 기존 {@code RefreshTokenStore}는
 * Redis 키를 역할 구분 없이 {@code userId}로만 관리해서, 그대로 재사용하면
 * {@code admins.id}와 {@code users.id}가 우연히 같을 때 서로 다른 사람의 리프레시
 * 토큰을 덮어쓸 수 있다. 액세스 토큰 만료 시 재로그인시키는 쪽이 더 단순하고 안전하다.
 */
public record AdminSession(String accessToken, String refreshToken, String adminName) {

    public static AdminSession of(String accessToken, String adminName) {
        return new AdminSession(accessToken, null, adminName);
    }
}
