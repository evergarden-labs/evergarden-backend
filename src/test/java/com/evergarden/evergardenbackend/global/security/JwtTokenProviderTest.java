package com.evergarden.evergardenbackend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 리프레시 토큰의 {@code jti} 발급·검증(ADR-055 전제 조건)을 확인한다. */
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-test-secret-key-0123456789";

    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(
            new JwtProperties(SECRET, 30, 7));

    @Test
    @DisplayName("리프레시 토큰은 발급할 때마다 다른 jti를 받는다")
    void 매번_다른_jti() {
        IssuedRefreshToken first = tokenProvider.issueRefreshToken(1L, Role.USER);
        IssuedRefreshToken second = tokenProvider.issueRefreshToken(1L, Role.USER);

        assertThat(first.jti()).isNotBlank();
        assertThat(second.jti()).isNotBlank();
        assertThat(first.jti()).isNotEqualTo(second.jti());
    }

    @Test
    @DisplayName("발급한 리프레시 토큰을 파싱하면 같은 jti·userId·role이 나온다")
    void 리프레시_토큰_라운드트립() {
        IssuedRefreshToken issued = tokenProvider.issueRefreshToken(5L, Role.ADMIN);

        RefreshTokenPrincipal parsed = tokenProvider.parseRefreshToken(issued.token());

        assertThat(parsed.userId()).isEqualTo(5L);
        assertThat(parsed.role()).isEqualTo(Role.ADMIN);
        assertThat(parsed.jti()).isEqualTo(issued.jti());
    }

    @Test
    @DisplayName("액세스 토큰은 parseRefreshToken으로 못 받는다 — type 클레임이 다르다")
    void 액세스_토큰은_리프레시로_파싱_불가() {
        String accessToken = tokenProvider.issueAccessToken(1L, Role.USER);

        assertThatThrownBy(() -> tokenProvider.parseRefreshToken(accessToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("type은 REFRESH인데 jti가 없는 토큰(구버전 가정)은 거부한다")
    void jti_없는_리프레시_토큰은_거부() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String legacyRefreshToken = Jwts.builder()
                .subject("1")
                .claim("role", "USER")
                .claim("type", "REFRESH")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(key)
                .compact();

        assertThatThrownBy(() -> tokenProvider.parseRefreshToken(legacyRefreshToken))
                .isInstanceOf(JwtException.class);
    }
}
