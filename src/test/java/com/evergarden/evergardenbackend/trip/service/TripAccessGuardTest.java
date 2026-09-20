package com.evergarden.evergardenbackend.trip.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.user.entity.User;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TripAccessGuardTest {

    private static final Long OWNER_ID = 1L;
    private static final Long STRANGER_ID = 2L;

    private final TripAccessGuard guard = new TripAccessGuard();

    private Trip trip() {
        User owner = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);
        return Trip.builder().owner(owner).title("제주 여행")
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 1, 3)).build();
    }

    @Test
    @DisplayName("소유자면 통과한다")
    void 소유자_통과() {
        assertThatCode(() -> guard.checkOwner(trip(), OWNER_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("소유자가 아니면 NOT_RESOURCE_OWNER")
    void 소유자아니면_거부() {
        assertThatThrownBy(() -> guard.checkOwner(trip(), STRANGER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }
}
