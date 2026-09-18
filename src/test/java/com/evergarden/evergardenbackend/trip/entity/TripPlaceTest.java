package com.evergarden.evergardenbackend.trip.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** {@code TripPlace} 엔티티의 순수 로직(자리 이동·메모)을 확인한다. */
class TripPlaceTest {

    private Trip trip() {
        User owner = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(owner, "id", 1L);
        return Trip.builder().owner(owner).title("제주 여행")
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 1, 3)).build();
    }

    private Place place() {
        return Place.builder().contentId("c1").contentTypeId("12").title("장소")
                .lat(BigDecimal.ONE).lng(BigDecimal.ONE).build();
    }

    private TripPlace tripPlace() {
        return TripPlace.builder().trip(trip()).place(place())
                .dayNumber((short) 1).sortOrder((short) 1).memo("원래 메모").build();
    }

    @Test
    void 자리를_옮기면_날짜와_순서가_둘_다_바뀐다() {
        TripPlace tripPlace = tripPlace();

        tripPlace.relocate((short) 2, (short) 3);

        assertThat(tripPlace.getDayNumber()).isEqualTo((short) 2);
        assertThat(tripPlace.getSortOrder()).isEqualTo((short) 3);
    }

    @Test
    void 메모를_바꾼다() {
        TripPlace tripPlace = tripPlace();

        tripPlace.updateMemo("새 메모");

        assertThat(tripPlace.getMemo()).isEqualTo("새 메모");
    }

    @Test
    void 메모를_null로_지울_수_있다() {
        TripPlace tripPlace = tripPlace();

        tripPlace.updateMemo(null);

        assertThat(tripPlace.getMemo()).isNull();
    }
}
