package com.evergarden.evergardenbackend.garden.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.evergarden.evergardenbackend.user.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 쿨다운 경계(ADR-018)를 확인한다. {@code RegionVisitIntegrationTest}가 실제로 잡았던
 * 버그(해금 직후 재인증하면 쿨다운 없이 곧장 자람)의 회귀를 여기서 직접 잠근다.
 */
class UserGardenObjectTest {

    private User user() {
        User user = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private GardenObject gardenObject(short maxStage) {
        return GardenObject.builder()
                .name("종로 은행나무").type(GardenObjectType.PLANT).maxStage(maxStage).build();
    }

    private UserGardenObject unlockedAt(LocalDateTime unlockedAt, short maxStage) {
        return UserGardenObject.builder()
                .user(user()).gardenObject(gardenObject(maxStage)).unlockedAt(unlockedAt).build();
    }

    @Test
    void 해금_직후엔_1단계다() {
        UserGardenObject object = unlockedAt(LocalDateTime.now(), (short) 3);

        assertThat(object.getStage()).isEqualTo((short) 1);
    }

    @Test
    void 해금_직후_바로_재인증하면_쿨다운이라_안_자란다() {
        LocalDateTime unlockedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        UserGardenObject object = unlockedAt(unlockedAt, (short) 3);

        boolean grew = object.growIfPossible(unlockedAt.plusMinutes(1), 7);

        assertThat(grew).isFalse();
        assertThat(object.getStage()).isEqualTo((short) 1);
    }

    @Test
    void 해금_후_7일_되기_전날엔_아직_쿨다운이다() {
        LocalDateTime unlockedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        UserGardenObject object = unlockedAt(unlockedAt, (short) 3);

        boolean grew = object.growIfPossible(unlockedAt.plusDays(7).minusSeconds(1), 7);

        assertThat(grew).isFalse();
    }

    @Test
    void 해금_후_정확히_7일째면_자란다() {
        LocalDateTime unlockedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        UserGardenObject object = unlockedAt(unlockedAt, (short) 3);

        boolean grew = object.growIfPossible(unlockedAt.plusDays(7), 7);

        assertThat(grew).isTrue();
        assertThat(object.getStage()).isEqualTo((short) 2);
    }

    @Test
    void 한번_자란_뒤엔_lastGrownAt_기준으로_다시_쿨다운이_걸린다() {
        LocalDateTime unlockedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        UserGardenObject object = unlockedAt(unlockedAt, (short) 3);
        object.growIfPossible(unlockedAt.plusDays(7), 7);

        boolean grewAgainTooSoon = object.growIfPossible(unlockedAt.plusDays(8), 7);

        assertThat(grewAgainTooSoon).isFalse();
        assertThat(object.getStage()).isEqualTo((short) 2);
    }

    @Test
    void 최대_단계면_쿨다운이_지나도_안_자란다() {
        LocalDateTime unlockedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        UserGardenObject object = unlockedAt(unlockedAt, (short) 1);

        boolean grew = object.growIfPossible(unlockedAt.plusDays(7), 7);

        assertThat(grew).isFalse();
        assertThat(object.getStage()).isEqualTo((short) 1);
    }

    @Test
    void nextGrowableAt은_한번도_안_자랐으면_unlockedAt_기준이다() {
        LocalDateTime unlockedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        UserGardenObject object = unlockedAt(unlockedAt, (short) 3);

        assertThat(object.nextGrowableAt(7)).isEqualTo(unlockedAt.plusDays(7));
    }

    @Test
    void nextGrowableAt은_한번_자란_뒤엔_lastGrownAt_기준이다() {
        LocalDateTime unlockedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        UserGardenObject object = unlockedAt(unlockedAt, (short) 3);
        LocalDateTime grownAt = unlockedAt.plusDays(7);
        object.growIfPossible(grownAt, 7);

        assertThat(object.nextGrowableAt(7)).isEqualTo(grownAt.plusDays(7));
    }
}
