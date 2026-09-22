package com.evergarden.evergardenbackend.timecapsule.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.evergarden.evergardenbackend.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** {@code TimeCapsule} 엔티티의 순수 로직(팩토리·상태 전환)을 확인한다. */
class TimeCapsuleTest {

    private User user(Long id) {
        User user = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    // ── 팩토리 ──────────────────────────────────────────────

    @Test
    void sealUntilDate로_만들면_DATE_타입이고_SEALED다() {
        TimeCapsule capsule = TimeCapsule.sealUntilDate(
                user(1L), "제목", "내용", LocalDate.of(2027, 1, 1));

        assertThat(capsule.getUnlockType()).isEqualTo(UnlockType.DATE);
        assertThat(capsule.getStatus()).isEqualTo(TimeCapsuleStatus.SEALED);
        assertThat(capsule.getUnlockDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(capsule.getUnlockLat()).isNull();
    }

    @Test
    void sealAtPlace로_만들면_LOCATION_타입이고_좌표가_채워진다() {
        TimeCapsule capsule = TimeCapsule.sealAtPlace(
                user(1L), "제목", "내용", BigDecimal.valueOf(33.36), BigDecimal.valueOf(126.53), 100, "한라산");

        assertThat(capsule.getUnlockType()).isEqualTo(UnlockType.LOCATION);
        assertThat(capsule.getStatus()).isEqualTo(TimeCapsuleStatus.SEALED);
        assertThat(capsule.getUnlockLat()).isEqualByComparingTo(BigDecimal.valueOf(33.36));
        assertThat(capsule.getUnlockLng()).isEqualByComparingTo(BigDecimal.valueOf(126.53));
        assertThat(capsule.getUnlockRadiusM()).isEqualTo(100);
        assertThat(capsule.getPlaceName()).isEqualTo("한라산");
        assertThat(capsule.getUnlockDate()).isNull();
    }

    // ── 상태 전환(TC-04·05) ───────────────────────────────────

    @Test
    void markUnlockable은_SEALED일_때만_UNLOCKABLE로_바뀐다() {
        TimeCapsule capsule = TimeCapsule.sealUntilDate(user(1L), "제목", "내용", LocalDate.of(2027, 1, 1));

        capsule.markUnlockable();

        assertThat(capsule.getStatus()).isEqualTo(TimeCapsuleStatus.UNLOCKABLE);
        assertThat(capsule.isUnlockable()).isTrue();
    }

    @Test
    void markUnlockable은_이미_UNLOCKABLE이면_그대로다() {
        TimeCapsule capsule = TimeCapsule.sealUntilDate(user(1L), "제목", "내용", LocalDate.of(2027, 1, 1));
        capsule.markUnlockable();

        capsule.markUnlockable();

        assertThat(capsule.getStatus()).isEqualTo(TimeCapsuleStatus.UNLOCKABLE);
    }

    @Test
    void markUnlockable은_이미_OPENED면_되돌리지_않는다() {
        TimeCapsule capsule = TimeCapsule.sealUntilDate(user(1L), "제목", "내용", LocalDate.of(2027, 1, 1));
        capsule.open(LocalDateTime.of(2027, 1, 2, 0, 0));

        capsule.markUnlockable();

        assertThat(capsule.getStatus()).isEqualTo(TimeCapsuleStatus.OPENED);
    }

    @Test
    void open하면_OPENED로_바뀌고_openedAt이_기록된다() {
        TimeCapsule capsule = TimeCapsule.sealUntilDate(user(1L), "제목", "내용", LocalDate.of(2027, 1, 1));
        LocalDateTime now = LocalDateTime.of(2027, 1, 2, 10, 30);

        capsule.open(now);

        assertThat(capsule.getStatus()).isEqualTo(TimeCapsuleStatus.OPENED);
        assertThat(capsule.getOpenedAt()).isEqualTo(now);
        assertThat(capsule.isOpened()).isTrue();
    }

    // ── 소유자 판별 ─────────────────────────────────────────

    @Test
    void 소유자_아이디가_같으면_true() {
        TimeCapsule capsule = TimeCapsule.sealUntilDate(user(1L), "제목", "내용", LocalDate.of(2027, 1, 1));

        assertThat(capsule.isOwnedBy(1L)).isTrue();
        assertThat(capsule.isOwnedBy(2L)).isFalse();
    }
}
