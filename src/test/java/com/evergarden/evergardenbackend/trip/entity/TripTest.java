package com.evergarden.evergardenbackend.trip.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.evergarden.evergardenbackend.user.entity.User;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** {@code Trip} 엔티티의 순수 로직(기간·소유자·계보)을 확인한다. */
class TripTest {

    private User user(Long id) {
        User user = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Trip trip(LocalDate start, LocalDate end) {
        return Trip.builder().owner(user(1L)).title("제주 여행").startDate(start).endDate(end).build();
    }

    @Test
    void 당일치기는_하루() {
        Trip trip = trip(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));

        assertThat(trip.durationDays()).isEqualTo(1);
    }

    @Test
    void 사흘_여행은_durationDays가_3() {
        Trip trip = trip(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));

        assertThat(trip.durationDays()).isEqualTo(3);
    }

    @Test
    void 소유자_아이디가_같으면_true() {
        Trip trip = trip(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));

        assertThat(trip.isOwnedBy(1L)).isTrue();
        assertThat(trip.isOwnedBy(2L)).isFalse();
    }

    @Test
    void 제목을_바꾼다() {
        Trip trip = trip(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));

        trip.updateTitle("새 제목");

        assertThat(trip.getTitle()).isEqualTo("새 제목");
    }

    @Test
    void 기간을_바꾸면_durationDays도_같이_바뀐다() {
        Trip trip = trip(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));

        trip.updatePeriod(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 5));

        assertThat(trip.getStartDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(trip.getEndDate()).isEqualTo(LocalDate.of(2026, 2, 5));
        assertThat(trip.durationDays()).isEqualTo(5);
    }

    @Test
    void 원본을_지정하면_originTrip으로_남는다() {
        Trip original = trip(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));
        Trip copy = Trip.builder().owner(user(2L)).title("복사본")
                .startDate(LocalDate.of(2026, 5, 1)).endDate(LocalDate.of(2026, 5, 3))
                .originTrip(original).build();

        assertThat(copy.getOriginTrip()).isSameAs(original);
    }

    @Test
    void 원본이_없으면_null() {
        Trip trip = trip(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));

        assertThat(trip.getOriginTrip()).isNull();
    }
}
