package com.evergarden.evergardenbackend.global.security;

import java.security.Principal;

/** STOMP 세션에 붙일 인증 주체. {@code Principal}은 이름 하나만 요구해서 감싼다. */
public record StompPrincipal(AuthPrincipal authPrincipal) implements Principal {

    @Override
    public String getName() {
        return authPrincipal.userId().toString();
    }
}
