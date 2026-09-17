package com.evergarden.evergardenbackend.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.place.repository.PlaceRepository;
import com.evergarden.evergardenbackend.trip.dto.TripDetail;
import com.evergarden.evergardenbackend.trip.dto.TripPlaceCreateRequest;
import com.evergarden.evergardenbackend.trip.dto.TripPlaceOrderRequest;
import com.evergarden.evergardenbackend.trip.dto.TripPlaceResponse;
import com.evergarden.evergardenbackend.trip.dto.TripPlaceUpdateRequest;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import com.evergarden.evergardenbackend.trip.repository.TripPlaceRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 일정에 장소 담기(PLAN-02·04·08)의 순서 처리·검증을 확인한다. */
class TripPlaceServiceTest {

    private static final Long USER_ID = 1L;

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripPlaceRepository tripPlaceRepository = mock(TripPlaceRepository.class);
    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final TripAccessGuard accessGuard = mock(TripAccessGuard.class);
    private final TripMapper tripMapper = mock(TripMapper.class);
    private final TripService tripService = mock(TripService.class);

    private final TripPlaceService tripPlaceService = new TripPlaceService(
            tripRepository, tripPlaceRepository, placeRepository, accessGuard, tripMapper, tripService);

    private Trip trip;

    @BeforeEach
    void setUp() {
        User owner = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(owner, "id", USER_ID);
        trip = Trip.builder().owner(owner).title("제주 여행")
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 1, 3)).build();
        ReflectionTestUtils.setField(trip, "id", 10L);
        given(tripRepository.findById(10L)).willReturn(Optional.of(trip));
        given(tripMapper.toPlaceResponse(any())).willReturn(mock(TripPlaceResponse.class));
    }

    private Place place(Long id) {
        Place place = Place.builder().contentId("c" + id).contentTypeId("12").title("장소" + id).build();
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }

    private TripPlace tripPlace(Long id, short day, short order) {
        TripPlace tp = TripPlace.builder().trip(trip).place(place(id)).dayNumber(day).sortOrder(order).build();
        ReflectionTestUtils.setField(tp, "id", id);
        return tp;
    }

    // ── 추가 ─────────────────────────────────────────────

    @Test
    @DisplayName("일정 기간 밖의 dayNumber면 INVALID_REQUEST")
    void 추가_기간밖_일자() {
        TripPlaceCreateRequest request = new TripPlaceCreateRequest(1L, (short) 9, null, null);

        assertThatThrownBy(() -> tripPlaceService.addPlace(USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
        verify(accessGuard).checkOwner(trip, USER_ID);
    }

    @Test
    @DisplayName("없는 장소를 담으려 하면 PLACE_NOT_FOUND")
    void 추가_없는장소() {
        given(placeRepository.findById(1L)).willReturn(Optional.empty());
        TripPlaceCreateRequest request = new TripPlaceCreateRequest(1L, (short) 1, null, null);

        assertThatThrownBy(() -> tripPlaceService.addPlace(USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLACE_NOT_FOUND);
    }

    @Test
    @DisplayName("sortOrder를 생략하면 그 날의 맨 뒤에 붙는다")
    void 추가_순서생략_맨뒤() {
        given(placeRepository.findById(1L)).willReturn(Optional.of(place(1L)));
        given(tripPlaceRepository.countByTripAndDayNumber(trip, (short) 1)).willReturn(2L);
        TripPlaceCreateRequest request = new TripPlaceCreateRequest(1L, (short) 1, null, null);

        tripPlaceService.addPlace(USER_ID, 10L, request);

        var captor = org.mockito.ArgumentCaptor.forClass(TripPlace.class);
        verify(tripPlaceRepository).save(captor.capture());
        assertThat(captor.getValue().getSortOrder()).isEqualTo((short) 3);
        verify(tripPlaceRepository, never()).findByTripAndDayNumberOrderBySortOrderDesc(any(), org.mockito.ArgumentMatchers.anyShort());
    }

    @Test
    @DisplayName("이미 있는 자리에 끼워 넣으면 그 자리부터 뒤쪽을 한 칸씩 민다")
    void 추가_충돌하면_뒤로밀기() {
        given(placeRepository.findById(1L)).willReturn(Optional.of(place(1L)));
        TripPlace existing1 = tripPlace(101L, (short) 1, (short) 1);
        TripPlace existing2 = tripPlace(102L, (short) 1, (short) 2);
        // DESC 순서로 반환 — makeRoom이 기대하는 순서
        given(tripPlaceRepository.findByTripAndDayNumberOrderBySortOrderDesc(trip, (short) 1))
                .willReturn(List.of(existing2, existing1));
        TripPlaceCreateRequest request = new TripPlaceCreateRequest(1L, (short) 1, (short) 1, null);

        tripPlaceService.addPlace(USER_ID, 10L, request);

        assertThat(existing1.getSortOrder()).isEqualTo((short) 2);
        assertThat(existing2.getSortOrder()).isEqualTo((short) 3);
    }

    // ── 수정 ─────────────────────────────────────────────

    @Test
    @DisplayName("빈 수정 요청은 INVALID_REQUEST")
    void 수정_빈요청() {
        assertThatThrownBy(() -> tripPlaceService.updatePlace(USER_ID, 10L, 101L, new TripPlaceUpdateRequest(null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("없는 tripPlace 수정은 TRIP_PLACE_NOT_FOUND")
    void 수정_없는항목() {
        given(tripPlaceRepository.findByIdAndTrip(101L, trip)).willReturn(Optional.empty());

        assertThatThrownBy(() -> tripPlaceService.updatePlace(USER_ID, 10L, 101L, new TripPlaceUpdateRequest(null, null, "메모")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRIP_PLACE_NOT_FOUND);
    }

    @Test
    @DisplayName("메모만 바꾸면 자리는 그대로 둔다")
    void 수정_메모만() {
        TripPlace tp = tripPlace(101L, (short) 1, (short) 1);
        given(tripPlaceRepository.findByIdAndTrip(101L, trip)).willReturn(Optional.of(tp));

        tripPlaceService.updatePlace(USER_ID, 10L, 101L, new TripPlaceUpdateRequest(null, null, "새 메모"));

        assertThat(tp.getMemo()).isEqualTo("새 메모");
        assertThat(tp.getSortOrder()).isEqualTo((short) 1);
        verify(tripPlaceRepository, never()).findByTripAndDayNumberOrderBySortOrderAsc(any(), org.mockito.ArgumentMatchers.anyShort());
    }

    @Test
    @DisplayName("다른 날로 옮기면 원래 자리를 당기고 새 자리를 민다")
    void 수정_다른날로이동() {
        TripPlace tp = tripPlace(101L, (short) 1, (short) 1);
        given(tripPlaceRepository.findByIdAndTrip(101L, trip)).willReturn(Optional.of(tp));
        given(tripPlaceRepository.findByTripAndDayNumberOrderBySortOrderAsc(trip, (short) 1)).willReturn(List.of());
        given(tripPlaceRepository.findByTripAndDayNumberOrderBySortOrderDesc(trip, (short) 2)).willReturn(List.of());
        given(tripPlaceRepository.countByTripAndDayNumber(trip, (short) 2)).willReturn(0L);

        tripPlaceService.updatePlace(USER_ID, 10L, 101L, new TripPlaceUpdateRequest((short) 2, null, null));

        assertThat(tp.getDayNumber()).isEqualTo((short) 2);
        assertThat(tp.getSortOrder()).isEqualTo((short) 1);
    }

    // ── 제거 ─────────────────────────────────────────────

    @Test
    @DisplayName("제거하면 뒤에 있던 자리들을 한 칸씩 당긴다")
    void 제거_뒤당기기() {
        TripPlace target = tripPlace(101L, (short) 1, (short) 1);
        TripPlace after = tripPlace(102L, (short) 1, (short) 2);
        given(tripPlaceRepository.findByIdAndTrip(101L, trip)).willReturn(Optional.of(target));
        given(tripPlaceRepository.findByTripAndDayNumberOrderBySortOrderAsc(trip, (short) 1)).willReturn(List.of(after));

        tripPlaceService.removePlace(USER_ID, 10L, 101L);

        verify(tripPlaceRepository).delete(target);
        assertThat(after.getSortOrder()).isEqualTo((short) 1);
    }

    // ── 순서 통째로 재배열 ────────────────────────────────

    @Test
    @DisplayName("보낸 항목 수가 현재 개수와 다르면 INVALID_REQUEST")
    void 재배열_개수불일치() {
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip))
                .willReturn(List.of(tripPlace(101L, (short) 1, (short) 1), tripPlace(102L, (short) 1, (short) 2)));
        TripPlaceOrderRequest request = new TripPlaceOrderRequest(
                List.of(new TripPlaceOrderRequest.Item(101L, (short) 1, (short) 1)));

        assertThatThrownBy(() -> tripPlaceService.replaceOrder(USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("존재하지 않는 tripPlaceId가 섞이면 TRIP_PLACE_NOT_FOUND")
    void 재배열_없는항목포함() {
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip))
                .willReturn(List.of(tripPlace(101L, (short) 1, (short) 1)));
        TripPlaceOrderRequest request = new TripPlaceOrderRequest(
                List.of(new TripPlaceOrderRequest.Item(999L, (short) 1, (short) 1)));

        assertThatThrownBy(() -> tripPlaceService.replaceOrder(USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRIP_PLACE_NOT_FOUND);
    }

    @Test
    @DisplayName("정상 재배열은 요청한 자리로 맞바꾸고 상세를 돌려준다")
    void 재배열_정상_맞바꾸기() {
        TripPlace a = tripPlace(101L, (short) 1, (short) 1);
        TripPlace b = tripPlace(102L, (short) 1, (short) 2);
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of(a, b));
        given(tripService.toDetail(trip)).willReturn(mock(TripDetail.class));

        TripPlaceOrderRequest request = new TripPlaceOrderRequest(List.of(
                new TripPlaceOrderRequest.Item(101L, (short) 1, (short) 2),
                new TripPlaceOrderRequest.Item(102L, (short) 1, (short) 1)));

        TripDetail result = tripPlaceService.replaceOrder(USER_ID, 10L, request);

        assertThat(result).isNotNull();
        assertThat(a.getSortOrder()).isEqualTo((short) 2);
        assertThat(b.getSortOrder()).isEqualTo((short) 1);
        verify(tripService, times(1)).toDetail(trip);
    }
}
