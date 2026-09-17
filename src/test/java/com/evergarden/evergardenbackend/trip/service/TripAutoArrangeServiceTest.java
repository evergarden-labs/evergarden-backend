package com.evergarden.evergardenbackend.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.trip.dto.AutoArrangeItem;
import com.evergarden.evergardenbackend.trip.dto.AutoArrangeRequest;
import com.evergarden.evergardenbackend.trip.dto.AutoArrangeResult;
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

/** 자동 배치 제안(PLAN-09)의 검증 순서와, 최근접+2-opt 재배치가 실제로 짧아지는지 확인한다. */
class TripAutoArrangeServiceTest {

    private static final Long USER_ID = 1L;

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripPlaceRepository tripPlaceRepository = mock(TripPlaceRepository.class);
    private final TripAccessGuard accessGuard = mock(TripAccessGuard.class);

    private final TripAutoArrangeService service =
            new TripAutoArrangeService(tripRepository, tripPlaceRepository, accessGuard);

    private Trip trip;

    @BeforeEach
    void setUp() {
        User owner = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(owner, "id", USER_ID);
        trip = Trip.builder().owner(owner).title("서울 여행")
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 1, 1)).build();
        ReflectionTestUtils.setField(trip, "id", 10L);
        given(tripRepository.findById(10L)).willReturn(Optional.of(trip));
    }

    private Place place(Long id, double lat, double lng) {
        Region region = Region.builder().code("11").level(RegionLevel.SIDO).name("서울")
                .centerLat(BigDecimal.ZERO).centerLng(BigDecimal.ZERO)
                .syncedAt(java.time.LocalDateTime.now()).build();
        Place place = Place.builder().contentId("c" + id).contentTypeId("12").title("장소" + id)
                .lat(BigDecimal.valueOf(lat)).lng(BigDecimal.valueOf(lng)).region(region).build();
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }

    private TripPlace tripPlace(Long id, short order, Place place) {
        TripPlace tp = TripPlace.builder().trip(trip).place(place).dayNumber((short) 1).sortOrder(order).build();
        ReflectionTestUtils.setField(tp, "id", id);
        return tp;
    }

    @Test
    @DisplayName("없는 일정이면 TRIP_NOT_FOUND")
    void 없는_일정() {
        given(tripRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.propose(USER_ID, 999L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRIP_NOT_FOUND);
    }

    @Test
    @DisplayName("소유자 확인을 거친다")
    void 소유자확인() {
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip))
                .willReturn(List.of(tripPlace(1L, (short) 1, place(1L, 37.5, 127.0))));

        service.propose(USER_ID, 10L, null);

        verify(accessGuard).checkOwner(trip, USER_ID);
    }

    @Test
    @DisplayName("담긴 장소가 없으면 INVALID_REQUEST")
    void 장소없음() {
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of());

        assertThatThrownBy(() -> service.propose(USER_ID, 10L, new AutoArrangeRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("장소가 1개뿐이면 그대로, 거리는 0")
    void 장소하나() {
        TripPlace tp = tripPlace(1L, (short) 1, place(1L, 37.5, 127.0));
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of(tp));

        AutoArrangeResult result = service.propose(USER_ID, 10L, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.currentDistanceMeters()).isZero();
        assertThat(result.proposedDistanceMeters()).isZero();
    }

    @Test
    @DisplayName("지그재그로 담긴 장소를 동선이 짧아지는 순서로 재배치한다")
    void 재배치로_거리가_짧아진다() {
        // 일직선 위의 4개 점을 일부러 지그재그(0, 30, 10, 20)로 담아둔다 — 최적 순서는 0,10,20,30
        Place p0 = place(1L, 37.500, 127.000);
        Place p30 = place(2L, 37.530, 127.000);
        Place p10 = place(3L, 37.510, 127.000);
        Place p20 = place(4L, 37.520, 127.000);
        List<TripPlace> current = List.of(
                tripPlace(1L, (short) 1, p0),
                tripPlace(2L, (short) 2, p30),
                tripPlace(3L, (short) 3, p10),
                tripPlace(4L, (short) 4, p20));
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(current);

        AutoArrangeResult result = service.propose(USER_ID, 10L, null);

        assertThat(result.proposedDistanceMeters()).isLessThan(result.currentDistanceMeters());
        List<Long> order = result.items().stream().map(AutoArrangeItem::tripPlaceId).toList();
        // 최적해는 원점에서 출발해 오름차순으로 쭉 가는 것 — 순방향이든 역방향이든 총 거리는 같다
        assertThat(order.get(0)).isEqualTo(1L);
        assertThat(order).containsExactly(1L, 3L, 4L, 2L);

        // items가 그대로 TripPlaceOrderRequest.items로 넘어갈 수 있어야 하므로 dayNumber·sortOrder를 채운다
        assertThat(result.items()).allSatisfy(item -> assertThat(item.dayNumber()).isEqualTo((short) 1));
        assertThat(result.items().stream().map(AutoArrangeItem::sortOrder).toList())
                .containsExactly((short) 1, (short) 2, (short) 3, (short) 4);
    }
}
