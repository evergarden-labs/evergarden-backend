package com.evergarden.evergardenbackend.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.evergarden.evergardenbackend.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 실제 Redis로 키 구조와 rotation·revoke 흐름을 확인한다(ADR-055).
 *
 * <p>{@code StringRedisTemplate}을 커스텀 설정 없이 그대로 주입받는다 —
 * {@code DataRedisAutoConfiguration}이 이미 빈을 만들어 준다.
 */
class RefreshTokenStoreTest extends IntegrationTest {

    @Autowired RefreshTokenStore store;
    @Autowired StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("저장하면 jti→userId, user→jti 두 키가 다 생긴다")
    void 저장() {
        Long userId = 9001L;
        store.save(userId, "jti-a");

        assertThat(redisTemplate.opsForValue().get("refresh:jti:jti-a")).isEqualTo("9001");
        assertThat(redisTemplate.opsForValue().get("refresh:user:9001")).isEqualTo("jti-a");
        assertThat(store.isValid(userId, "jti-a")).isTrue();
    }

    @Test
    @DisplayName("같은 유저가 다시 저장하면 이전 jti는 무효가 된다 — 유저당 하나만 유효(ADR-055)")
    void 재저장하면_이전_jti_무효() {
        Long userId = 9002L;
        store.save(userId, "jti-old");
        store.save(userId, "jti-new");

        assertThat(store.isValid(userId, "jti-old")).isFalse();
        assertThat(store.isValid(userId, "jti-new")).isTrue();
        assertThat(redisTemplate.opsForValue().get("refresh:user:9002")).isEqualTo("jti-new");
    }

    @Test
    @DisplayName("다른 유저의 jti로 검증하면 false — 값이 userId와 다르다")
    void 다른_유저_jti는_무효() {
        Long userId = 9003L;
        store.save(userId, "jti-b");

        assertThat(store.isValid(9999L, "jti-b")).isFalse();
    }

    @Test
    @DisplayName("모르는 jti는 무효")
    void 모르는_jti는_무효() {
        assertThat(store.isValid(9004L, "never-issued")).isFalse();
    }

    @Test
    @DisplayName("revoke하면 두 키가 모두 지워진다 — 로그아웃·탈퇴가 쓰는 흐름")
    void revoke_두키_삭제() {
        Long userId = 9005L;
        store.save(userId, "jti-c");

        store.revoke(userId);

        assertThat(redisTemplate.opsForValue().get("refresh:jti:jti-c")).isNull();
        assertThat(redisTemplate.opsForValue().get("refresh:user:9005")).isNull();
        assertThat(store.isValid(userId, "jti-c")).isFalse();
    }

    @Test
    @DisplayName("저장된 적 없는 유저를 revoke해도 예외 없이 통과한다")
    void 저장된적_없는_유저_revoke는_무해() {
        store.revoke(9006L);
    }
}
