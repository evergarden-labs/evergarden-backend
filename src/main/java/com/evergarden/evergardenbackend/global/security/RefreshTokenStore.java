package com.evergarden.evergardenbackend.global.security;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰을 Redis에 {@code jti} 기준으로 저장한다(ADR-055).
 *
 * <p>키 두 개를 쌍으로 관리한다 — {@code refresh:jti:{jti}} → {@code userId}(검증용),
 * {@code refresh:user:{userId}} → {@code jti}(로그아웃용 역인덱스). 둘 다 TTL을
 * {@code jwt.refresh-token-expiration-days}와 맞춘다.
 *
 * <p><b>유저당 하나만 유효하다.</b> {@link #save}가 저장하기 전에 그 유저의 기존 jti를
 * 먼저 지운다 — 재발급(rotation)뿐 아니라 재로그인에도 똑같이 적용돼, 한 사용자가
 * 두 기기에서 로그인하면 나중 로그인이 먼저 것의 리프레시 토큰을 무효화한다(ADR-055 한계).
 */
@Component
public class RefreshTokenStore {

    private static final String JTI_KEY_PREFIX = "refresh:jti:";
    private static final String USER_KEY_PREFIX = "refresh:user:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RefreshTokenStore(StringRedisTemplate redisTemplate, JwtProperties properties) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofDays(properties.refreshTokenExpirationDays());
    }

    /**
     * 새로 발급한 리프레시 토큰을 저장한다. 로그인·복구·재발급 어디서 불러도 같다 —
     * 항상 그 유저의 이전 jti를 먼저 지우고 새로 쓴다.
     */
    public void save(Long userId, String jti) {
        revoke(userId);
        redisTemplate.opsForValue().set(jtiKey(jti), String.valueOf(userId), ttl);
        redisTemplate.opsForValue().set(userKey(userId), jti, ttl);
    }

    /**
     * {@code jti}가 지금 이 유저의 유효한 리프레시 토큰인지 확인한다.
     * 재발급(rotation)으로 이미 지워졌거나 TTL이 지났으면 {@code false}다.
     */
    public boolean isValid(Long userId, String jti) {
        String storedUserId = redisTemplate.opsForValue().get(jtiKey(jti));
        return storedUserId != null && storedUserId.equals(String.valueOf(userId));
    }

    /**
     * 로그아웃·탈퇴 — 역인덱스로 지금 유효한 jti를 찾아 두 키를 모두 지운다.
     * 액세스 토큰만으로 호출되므로(리프레시 토큰을 안 받음) 이 조회 방향이 꼭 필요하다.
     */
    public void revoke(Long userId) {
        String jti = redisTemplate.opsForValue().get(userKey(userId));
        if (jti != null) {
            redisTemplate.delete(jtiKey(jti));
        }
        redisTemplate.delete(userKey(userId));
    }

    private String jtiKey(String jti) {
        return JTI_KEY_PREFIX + jti;
    }

    private String userKey(Long userId) {
        return USER_KEY_PREFIX + userId;
    }
}
