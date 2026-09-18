package com.evergarden.evergardenbackend.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.dto.PlaceSummary;
import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.PlaceRepository;
import com.evergarden.evergardenbackend.trip.dto.AutoArrangeApplyRequest;
import com.evergarden.evergardenbackend.trip.dto.AutoArrangeItem;
import com.evergarden.evergardenbackend.trip.dto.AutoArrangeRequest;
import com.evergarden.evergardenbackend.trip.dto.AutoArrangeResult;
import com.evergarden.evergardenbackend.trip.dto.TripDetail;
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
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 자동 배치 제안(PLAN-09)의 검증 순서, 최근접+2-opt 재배치, {@code additionalPlaceIds}의
 * 날짜 배정, 그리고 제안을 실제로 적용하는 {@code apply}(ADR-060)를 확인한다.
 */
class TripAutoArrangeServiceTest {

    private static final Long USER_ID = 1L;

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripPlaceRepository tripPlaceRepository = mock(TripPlaceRepository.class);
    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final TripAccessGuard accessGuard = mock(TripAccessGuard.class);
    private final TripService tripService = mock(TripService.class);

    private final TripAutoArrangeService service =
            new TripAutoArrangeService(tripRepository, tripPlaceRepository, placeRepository, accessGuard, tripService);

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

    private TripPlace tripPlace(Long id, short day, short order, Place place) {
        TripPlace tp = TripPlace.builder().trip(trip).place(place).dayNumber(day).sortOrder(order).build();
        ReflectionTestUtils.setField(tp, "id", id);
        return tp;
    }

    // ── 제안(propose) — 기본 검증·재배치 ──────────────────

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
                .willReturn(List.of(tripPlace(1L, (short) 1, (short) 1, place(1L, 37.5, 127.0))));

        service.propose(USER_ID, 10L, null);

        verify(accessGuard).checkOwner(trip, USER_ID);
    }

    @Test
    @DisplayName("담긴 장소도 additionalPlaceIds도 없으면 INVALID_REQUEST")
    void 장소없음() {
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of());

        assertThatThrownBy(() -> service.propose(USER_ID, 10L, new AutoArrangeRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("장소가 1개뿐이면 그대로, 거리는 0")
    void 장소하나() {
        TripPlace tp = tripPlace(1L, (short) 1, (short) 1, place(1L, 37.5, 127.0));
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
                tripPlace(1L, (short) 1, (short) 1, p0),
                tripPlace(2L, (short) 1, (short) 2, p30),
                tripPlace(3L, (short) 1, (short) 3, p10),
                tripPlace(4L, (short) 1, (short) 4, p20));
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

    // ── 제안(propose) — additionalPlaceIds(ADR-060) ───────

    @Test
    @DisplayName("없는 장소를 additionalPlaceIds로 넣으면 PLACE_NOT_FOUND")
    void 추가장소_없음() {
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip))
                .willReturn(List.of(tripPlace(1L, (short) 1, (short) 1, place(1L, 37.5, 127.0))));
        given(placeRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.propose(USER_ID, 10L, new AutoArrangeRequest(List.of(999L))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLACE_NOT_FOUND);
    }

    @Test
    @DisplayName("additionalPlaceIds로 넣은 장소는 tripPlaceId가 null로 items에 나타나고, currentDistance엔 안 들어간다")
    void 추가장소_null로_포함() {
        TripPlace existing = tripPlace(1L, (short) 1, (short) 1, place(1L, 37.500, 127.000));
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of(existing));
        Place added = place(2L, 37.501, 127.000);
        given(placeRepository.findById(2L)).willReturn(Optional.of(added));

        AutoArrangeResult result = service.propose(USER_ID, 10L, new AutoArrangeRequest(List.of(2L)));

        assertThat(result.currentDistanceMeters()).isZero(); // 기존 1개뿐이라 저장분 거리는 0
        assertThat(result.items()).hasSize(2);
        AutoArrangeItem newItem = result.items().stream().filter(i -> i.tripPlaceId() == null).findFirst().orElseThrow();
        assertThat(newItem.place().placeId()).isEqualTo(2L);
        assertThat(result.proposedDistanceMeters()).isGreaterThan(0); // 새 장소가 들어가 거리가 생김
    }

    @Test
    @DisplayName("이틀 일정에서, 각 날의 장소와 훨씬 가까운 쪽 날짜로 배정된다")
    void 추가장소_가까운날_배정() {
        Trip twoDayTrip = Trip.builder().owner(trip.getOwner()).title("이틀")
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 1, 2)).build();
        ReflectionTestUtils.setField(twoDayTrip, "id", 20L);
        given(tripRepository.findById(20L)).willReturn(Optional.of(twoDayTrip));

        TripPlace day1 = tripPlace(1L, (short) 1, (short) 1, place(1L, 37.500, 127.000));
        TripPlace day2 = tripPlace(2L, (short) 2, (short) 1, place(2L, 38.500, 127.000)); // 약 111km 북쪽
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(twoDayTrip)).willReturn(List.of(day1, day2));

        Place nearDay2 = place(3L, 38.501, 127.000); // day2 장소 바로 옆
        given(placeRepository.findById(3L)).willReturn(Optional.of(nearDay2));

        AutoArrangeResult result = service.propose(USER_ID, 20L, new AutoArrangeRequest(List.of(3L)));

        AutoArrangeItem newItem = result.items().stream().filter(i -> i.tripPlaceId() == null).findFirst().orElseThrow();
        assertThat(newItem.dayNumber()).isEqualTo((short) 2);
    }

    // ── 적용(apply) — ADR-060 ──────────────────────────────

    @Test
    @DisplayName("없는 일정이면 TRIP_NOT_FOUND")
    void 적용_없는일정() {
        given(tripRepository.findById(999L)).willReturn(Optional.empty());
        AutoArrangeApplyRequest request = new AutoArrangeApplyRequest(List.of());

        assertThatThrownBy(() -> service.apply(USER_ID, 999L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRIP_NOT_FOUND);
    }

    @Test
    @DisplayName("기존 장소 중 하나가 빠지면(부분 전송) INVALID_REQUEST")
    void 적용_부분전송_거부() {
        TripPlace a = tripPlace(1L, (short) 1, (short) 1, place(1L, 37.5, 127.0));
        TripPlace b = tripPlace(2L, (short) 1, (short) 2, place(2L, 37.6, 127.0));
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of(a, b));

        AutoArrangeApplyRequest request = new AutoArrangeApplyRequest(
                List.of(new AutoArrangeItem(1L, (short) 1, (short) 1, PlaceSummary.of(a.getPlace()))));

        assertThatThrownBy(() -> service.apply(USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("존재하지 않는 tripPlaceId가 섞이면 TRIP_PLACE_NOT_FOUND")
    void 적용_없는tripPlaceId() {
        TripPlace a = tripPlace(1L, (short) 1, (short) 1, place(1L, 37.5, 127.0));
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of(a));

        AutoArrangeApplyRequest request = new AutoArrangeApplyRequest(
                List.of(new AutoArrangeItem(999L, (short) 1, (short) 1, PlaceSummary.of(a.getPlace()))));

        assertThatThrownBy(() -> service.apply(USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRIP_PLACE_NOT_FOUND);
    }

    @Test
    @DisplayName("dayNumber가 일정 기간 밖이면 INVALID_REQUEST")
    void 적용_기간밖_날짜() {
        TripPlace a = tripPlace(1L, (short) 1, (short) 1, place(1L, 37.5, 127.0));
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of(a));

        AutoArrangeApplyRequest request = new AutoArrangeApplyRequest(
                List.of(new AutoArrangeItem(1L, (short) 9, (short) 1, PlaceSummary.of(a.getPlace()))));

        assertThatThrownBy(() -> service.apply(USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("같은 날짜·순서가 두 항목에서 겹치면 INVALID_REQUEST")
    void 적용_자리겹침() {
        TripPlace a = tripPlace(1L, (short) 1, (short) 1, place(1L, 37.5, 127.0));
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of(a));
        Place newPlace = place(2L, 37.6, 127.0);

        AutoArrangeApplyRequest request = new AutoArrangeApplyRequest(List.of(
                new AutoArrangeItem(1L, (short) 1, (short) 1, PlaceSummary.of(a.getPlace())),
                new AutoArrangeItem(null, (short) 1, (short) 1, PlaceSummary.of(newPlace))));

        assertThatThrownBy(() -> service.apply(USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("tripPlaceId가 있는 항목은 자리만 옮기고, null인 항목은 새로 추가한다")
    void 적용_정상() {
        TripPlace a = tripPlace(1L, (short) 1, (short) 1, place(1L, 37.5, 127.0));
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of(a));
        Place newPlace = place(2L, 37.6, 127.0);
        given(placeRepository.findById(2L)).willReturn(Optional.of(newPlace));
        given(tripService.toDetail(trip)).willReturn(mock(TripDetail.class));

        AutoArrangeApplyRequest request = new AutoArrangeApplyRequest(List.of(
                new AutoArrangeItem(1L, (short) 1, (short) 2, PlaceSummary.of(a.getPlace())),
                new AutoArrangeItem(null, (short) 1, (short) 1, PlaceSummary.of(newPlace))));

        TripDetail result = service.apply(USER_ID, 10L, request);

        assertThat(result).isNotNull();
        assertThat(a.getDayNumber()).isEqualTo((short) 1);
        assertThat(a.getSortOrder()).isEqualTo((short) 2);

        ArgumentCaptor<List<TripPlace>> captor = ArgumentCaptor.forClass(List.class);
        verify(tripPlaceRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        TripPlace created = captor.getValue().get(0);
        assertThat(created.getPlace()).isSameAs(newPlace);
        assertThat(created.getDayNumber()).isEqualTo((short) 1);
        assertThat(created.getSortOrder()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("없는 placeId로 새 항목을 넣으려 하면 PLACE_NOT_FOUND")
    void 적용_없는장소_추가() {
        TripPlace a = tripPlace(1L, (short) 1, (short) 1, place(1L, 37.5, 127.0));
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of(a));
        Place missing = place(999L, 37.6, 127.0);
        given(placeRepository.findById(999L)).willReturn(Optional.empty());

        AutoArrangeApplyRequest request = new AutoArrangeApplyRequest(List.of(
                new AutoArrangeItem(1L, (short) 1, (short) 1, PlaceSummary.of(a.getPlace())),
                new AutoArrangeItem(null, (short) 1, (short) 2, PlaceSummary.of(missing))));

        assertThatThrownBy(() -> service.apply(USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLACE_NOT_FOUND);
        verify(tripPlaceRepository, never()).saveAll(any());
    }
}
