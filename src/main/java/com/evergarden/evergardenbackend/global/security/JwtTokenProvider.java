package com.evergarden.evergardenbackend.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 발급과 검증. 서명 키를 아는 유일한 곳이다.
 *
 * <p>담는 클레임은 셋뿐이다 — {@code sub}(사용자 ID), {@code role}, {@code type}.
 * 닉네임이나 상태 같은 값은 넣지 않는다. 토큰은 발급 시점에 박제되므로
 * 차단·탈퇴가 반영되지 않아 <b>낡은 값으로 판단하게 된다</b>.
 * 바뀔 수 있는 것은 {@link JwtAuthenticationFilter}가 매 요청 DB에서 읽는다.
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secretKey().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(properties.accessTokenExpirationMinutes());
        this.refreshTokenTtl = Duration.ofDays(properties.refreshTokenExpirationDays());
    }

    /** API 호출에 쓸 액세스 토큰. */
    public String issueAccessToken(Long userId, Role role) {
        return issue(userId, role, TokenType.ACCESS, accessTokenTtl);
    }

    /** 재발급에 쓸 리프레시 토큰. */
    public String issueRefreshToken(Long userId, Role role) {
        return issue(userId, role, TokenType.REFRESH, refreshTokenTtl);
    }

    private String issue(Long userId, Role role, TokenType type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TYPE, type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * 액세스 토큰을 검증하고 주체를 꺼낸다.
     *
     * @throws ExpiredJwtException 만료됨 → {@code TOKEN_EXPIRED}. 재발급 대상이다
     * @throws JwtException        서명 불일치·변조·형식 오류·용도 불일치 → {@code TOKEN_INVALID}
     */
    public AuthPrincipal parseAccessToken(String token) {
        return parse(token, TokenType.ACCESS);
    }

    /** 리프레시 토큰을 검증하고 주체를 꺼낸다. {@code POST /auth/token/refresh}가 쓴다. */
    public AuthPrincipal parseRefreshToken(String token) {
        return parse(token, TokenType.REFRESH);
    }

    private AuthPrincipal parse(String token, TokenType expected) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)   // 서명이 틀리면 여기서 예외
                .getPayload();

        if (!expected.name().equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("expected " + expected + " token");
        }
        return new AuthPrincipal(
                Long.valueOf(claims.getSubject()),
                Role.valueOf(claims.get(CLAIM_ROLE, String.class)));
    }
}
