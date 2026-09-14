package com.evergarden.evergardenbackend.global.security;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 로그인 없이 개발할 때 쓰는 고정 사용자 ID를 들고 있는다.
 *
 * <p>{@link DevUserSeeder}가 기동 시 채우고 {@link JwtAuthenticationFilter}가 매 요청 읽는다.
 * {@code dev-auth.enabled=false}(기본값)면 아무도 채우지 않고 아무도 읽지 않는다.
 */
@Component
public class DevUserProvider {

    private final AtomicReference<Long> userId = new AtomicReference<>();

    public Long userId() {
        return userId.get();
    }

    void set(Long id) {
        userId.set(id);
    }
}
