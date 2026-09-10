package com.evergarden.evergardenbackend.global.security;

/**
 * 토큰의 역할 클레임(ADR-036).
 *
 * <p>관리자도 일반 회원과 같은 {@code bearerAuth} 스킴을 쓴다. 인증 처리를 두 벌
 * 만들지 않기 위해서다. 계정 자체는 {@code users}와 {@code admins}로 나뉘어 있어
 * 소셜 전용 정책은 그대로 지켜진다.
 */
public enum Role {

    /** 소셜 로그인으로 가입한 일반 회원. {@code users} 테이블. */
    USER,

    /** 아이디·비밀번호로 로그인하는 운영자. {@code admins} 테이블. */
    ADMIN;

    /** 스프링 시큐리티가 {@code hasRole("ADMIN")}에서 찾는 권한 이름. */
    public String authority() {
        return "ROLE_" + name();
    }
}
