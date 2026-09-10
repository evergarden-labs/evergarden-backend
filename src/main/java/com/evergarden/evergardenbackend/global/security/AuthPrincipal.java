package com.evergarden.evergardenbackend.global.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 인증된 요청 주체. 토큰에서 꺼낸 값만 담는다.
 *
 * <p>컨트롤러에서 {@code @AuthenticationPrincipal AuthPrincipal me}로 받는다.
 *
 * <p>엔티티({@code User})를 담지 않는 이유 — 요청마다 영속성 컨텍스트가 다르다.
 * 필터에서 읽은 엔티티를 컨트롤러까지 들고 가면 준영속 상태가 섞여 다루기 어려워진다.
 * 필요하면 서비스가 {@code userId}로 다시 조회한다.
 *
 * @param userId {@link Role#USER}면 {@code users.id}, {@link Role#ADMIN}이면 {@code admins.id}
 */
public record AuthPrincipal(Long userId, Role role) {

    public Collection<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
