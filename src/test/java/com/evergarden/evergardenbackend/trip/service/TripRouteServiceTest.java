package com.evergarden.evergardenbackend.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.trip.dto.TripRoute;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import com.evergarden.evergardenbackend.trip.repository.TripPlaceRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 코스 시각화 데이터 조회(PLAN-10)의 좌표·거리·경계상자 계산을 확인한다. */
class TripRouteServiceTest {

    private static final Long USER_ID = 1L;

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripPlaceRepository tripPlaceRepository = mock(TripPlaceRepository.class);
    private final TripAccessGuard accessGuard = mock(TripAccessGuard.class);

    private final TripRouteService tripRouteService =
            new TripRouteService(tripRepository, tripPlaceRepository, accessGuard);

    private Trip trip;

    @BeforeEach
    void setUp() {
        User owner = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(owner, "id", USER_ID);
        trip = Trip.builder().owner(owner).title("제주 여행")
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 1, 2)).build();
        ReflectionTestUtils.setField(trip, "id", 10L);
        given(tripRepository.findById(10L)).willReturn(Optional.of(trip));
    }

    private Place place(Long id, String title, double lat, double lng) {
        Place place = Place.builder().contentId("c" + id).contentTypeId("12").title(title)
                .lat(BigDecimal.valueOf(lat)).lng(BigDecimal.valueOf(lng)).build();
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }

    private TripPlace tripPlace(Long id, short day, short order, Place place) {
        TripPlace tp = TripPlace.builder().trip(trip).place(place).dayNumber(day).sortOrder(order).build();
        ReflectionTestUtils.setField(tp, "id", id);
        return tp;
    }

    @Test
    @DisplayName("없는 일정 조회는 TRIP_NOT_FOUND")
    void 없는_일정() {
        given(tripRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> tripRouteService.getRoute(USER_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRIP_NOT_FOUND);
    }

    @Test
    @DisplayName("조회는 소유자 확인을 거친다")
    void 소유자확인() {
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of());

        tripRouteService.getRoute(USER_ID, 10L);

        verify(accessGuard).checkOwner(trip, USER_ID);
    }

    @Test
    @DisplayName("장소가 없는 일정은 모든 날짜가 빈 좌표, 경계상자는 null")
    void 장소없음() {
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of());

        TripRoute route = tripRouteService.getRoute(USER_ID, 10L);

        assertThat(route.days()).hasSize(2);
        assertThat(route.days()).allSatisfy(day -> {
            assertThat(day.points()).isEmpty();
            assertThat(day.totalDistanceMeters()).isNull();
        });
        assertThat(route.boundingBox()).isNull();
    }

    @Test
    @DisplayName("좌표가 1개뿐인 날은 이동 거리가 null")
    void 좌표하나뿐() {
        Place seoul = place(1L, "서울역", 37.5547, 126.9707);
        TripPlace tp = tripPlace(101L, (short) 1, (short) 1, seoul);
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of(tp));

        TripRoute route = tripRouteService.getRoute(USER_ID, 10L);

        assertThat(route.days().get(0).points()).hasSize(1);
        assertThat(route.days().get(0).totalDistanceMeters()).isNull();
        assertThat(route.boundingBox()).isNotNull();
    }

    @Test
    @DisplayName("좌표 2개 이상이면 하버사인 거리 합을 구하고, 경계상자는 전체 장소를 감싼다")
    void 거리와_경계상자() {
        // 서울역(37.5547,126.9707) ~ 부산역(35.1156,129.0403) 직선거리는 약 325km
        Place seoul = place(1L, "서울역", 37.5547, 126.9707);
        Place busan = place(2L, "부산역", 35.1156, 129.0403);
        TripPlace tp1 = tripPlace(101L, (short) 1, (short) 1, seoul);
        TripPlace tp2 = tripPlace(102L, (short) 1, (short) 2, busan);
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of(tp1, tp2));

        TripRoute route = tripRouteService.getRoute(USER_ID, 10L);

        Long distance = route.days().get(0).totalDistanceMeters();
        assertThat(distance).isNotNull();
        assertThat(distance).isBetween(320_000L, 330_000L);

        assertThat(route.boundingBox().minLat()).isEqualTo(35.1156);
        assertThat(route.boundingBox().maxLat()).isEqualTo(37.5547);
        assertThat(route.boundingBox().minLng()).isEqualTo(126.9707);
        assertThat(route.boundingBox().maxLng()).isEqualTo(129.0403);
    }
}
