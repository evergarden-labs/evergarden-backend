package com.evergarden.evergardenbackend.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 발급과 검증. 서명 키를 아는 유일한 곳이다.
 *
 * <p>담는 클레임은 {@code sub}(사용자 ID), {@code role}, {@code type} 셋에 더해
 * 리프레시 토큰에만 {@code jti}(고유 ID)가 붙는다. 닉네임이나 상태 같은 값은 넣지 않는다.
 * 토큰은 발급 시점에 박제되므로 차단·탈퇴가 반영되지 않아 <b>낡은 값으로 판단하게 된다</b>.
 * 바뀔 수 있는 것은 {@link JwtAuthenticationFilter}가 매 요청 DB에서 읽는다.
 *
 * <p>{@code jti}는 액세스 토큰엔 없다 — Redis 조회 대상이 아니라서 굳이 심을 이유가 없다(ADR-055).
 * 리프레시 토큰에만 심어 {@code refresh:jti:{jti}} 키로 쓴다.
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
        return issue(userId, role, TokenType.ACCESS, accessTokenTtl, null);
    }

    /**
     * 재발급에 쓸 리프레시 토큰. 매번 새 {@code jti}를 심는다 — 호출한 쪽이 이 값으로
     * {@code refresh:jti:{jti}} → {@code userId}를 Redis에 써야 한다(ADR-055).
     */
    public IssuedRefreshToken issueRefreshToken(Long userId, Role role) {
        String jti = UUID.randomUUID().toString();
        String token = issue(userId, role, TokenType.REFRESH, refreshTokenTtl, jti);
        return new IssuedRefreshToken(token, jti);
    }

    private String issue(Long userId, Role role, TokenType type, Duration ttl, String jti) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TYPE, type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)));
        if (jti != null) {
            builder.id(jti);
        }
        return builder.signWith(key).compact();
    }

    /**
     * 액세스 토큰을 검증하고 주체를 꺼낸다.
     *
     * @throws ExpiredJwtException 만료됨 → {@code TOKEN_EXPIRED}. 재발급 대상이다
     * @throws JwtException        서명 불일치·변조·형식 오류·용도 불일치 → {@code TOKEN_INVALID}
     */
    public AuthPrincipal parseAccessToken(String token) {
        Claims claims = parse(token, TokenType.ACCESS);
        return new AuthPrincipal(
                Long.valueOf(claims.getSubject()),
                Role.valueOf(claims.get(CLAIM_ROLE, String.class)));
    }

    /**
     * 리프레시 토큰을 검증하고 {@code jti}까지 꺼낸다. {@code POST /auth/token/refresh}가 쓴다.
     *
     * @throws JwtException {@code jti}가 없는 토큰(구버전) — {@code claims.getId()}가 {@code null}이면
     *                       Redis 키를 만들 수 없어 검증되지 않은 토큰과 동일하게 취급한다
     */
    public RefreshTokenPrincipal parseRefreshToken(String token) {
        Claims claims = parse(token, TokenType.REFRESH);
        String jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            throw new JwtException("refresh token missing jti");
        }
        return new RefreshTokenPrincipal(
                Long.valueOf(claims.getSubject()),
                Role.valueOf(claims.get(CLAIM_ROLE, String.class)),
                jti);
    }

    private Claims parse(String token, TokenType expected) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)   // 서명이 틀리면 여기서 예외
                .getPayload();

        if (!expected.name().equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("expected " + expected + " token");
        }
        return claims;
    }
}
