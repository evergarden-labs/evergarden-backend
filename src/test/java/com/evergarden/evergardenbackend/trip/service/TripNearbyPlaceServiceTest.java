package com.evergarden.evergardenbackend.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.dto.PlaceSummary;
import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.PlaceRepository;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import com.evergarden.evergardenbackend.trip.repository.TripPlaceRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/** 주변 추천 장소 조회(PLAN-08)의 반경·제외·정렬 로직을 확인한다. */
class TripNearbyPlaceServiceTest {

    private static final Long USER_ID = 1L;

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripPlaceRepository tripPlaceRepository = mock(TripPlaceRepository.class);
    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final TripAccessGuard accessGuard = mock(TripAccessGuard.class);

    private final TripNearbyPlaceService service =
            new TripNearbyPlaceService(tripRepository, tripPlaceRepository, placeRepository, accessGuard);

    private Trip trip;
    private Region region;

    @BeforeEach
    void setUp() {
        User owner = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(owner, "id", USER_ID);
        trip = Trip.builder().owner(owner).title("서울 여행")
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 1, 2)).build();
        ReflectionTestUtils.setField(trip, "id", 10L);
        given(tripRepository.findById(10L)).willReturn(Optional.of(trip));

        region = Region.builder().code("11").level(RegionLevel.SIDO).name("서울")
                .centerLat(BigDecimal.ZERO).centerLng(BigDecimal.ZERO).syncedAt(LocalDateTime.now()).build();
    }

    private Place place(Long id, double lat, double lng) {
        Place place = Place.builder().contentId("c" + id).contentTypeId("12").title("장소" + id)
                .lat(BigDecimal.valueOf(lat)).lng(BigDecimal.valueOf(lng)).region(region).build();
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }

    private TripPlace tripPlace(Long id, short day, Place place) {
        TripPlace tp = TripPlace.builder().trip(trip).place(place).dayNumber(day).sortOrder((short) 1).build();
        ReflectionTestUtils.setField(tp, "id", id);
        return tp;
    }

    @Test
    @DisplayName("없는 일정이면 TRIP_NOT_FOUND")
    void 없는_일정() {
        given(tripRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.listNearby(USER_ID, 999L, null, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRIP_NOT_FOUND);
    }

    @Test
    @DisplayName("소유자 확인을 거친다")
    void 소유자확인() {
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of());

        service.listNearby(USER_ID, 10L, null, PageRequest.of(0, 20));

        verify(accessGuard).checkOwner(trip, USER_ID);
    }

    @Test
    @DisplayName("기준 장소가 없으면(그 날짜에 담긴 게 없음) 빈 목록")
    void 기준장소_없음() {
        Place base = place(1L, 37.5, 127.0);
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip))
                .willReturn(List.of(tripPlace(101L, (short) 1, base)));

        Page<PlaceSummary> result = service.listNearby(USER_ID, 10L, (short) 2, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("반경 5km 밖은 빠지고, 이미 담긴 장소도 빠진다")
    void 반경밖_제외_이미담긴장소_제외() {
        Place base = place(1L, 37.500, 127.000);
        Place alreadyIn = place(2L, 37.501, 127.000); // 가깝지만 이미 일정에 있음
        Place farAway = place(3L, 38.500, 127.000);   // 반경 훨씬 밖(약 111km)
        Place nearby = place(4L, 37.510, 127.000);    // 약 1.1km — 후보

        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of(
                tripPlace(101L, (short) 1, base), tripPlace(102L, (short) 1, alreadyIn)));
        given(placeRepository.findByLatBetweenAndLngBetween(any(), any(), any(), any()))
                .willReturn(List.of(base, alreadyIn, farAway, nearby));

        Page<PlaceSummary> result = service.listNearby(USER_ID, 10L, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(PlaceSummary::placeId).containsExactly(4L);
    }

    @Test
    @DisplayName("가까운 순으로 정렬된다")
    void 가까운순_정렬() {
        Place base = place(1L, 37.500, 127.000);
        Place far = place(2L, 37.530, 127.000);  // 약 3.3km
        Place near = place(3L, 37.505, 127.000); // 약 0.55km

        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip))
                .willReturn(List.of(tripPlace(101L, (short) 1, base)));
        given(placeRepository.findByLatBetweenAndLngBetween(any(), any(), any(), any()))
                .willReturn(List.of(base, far, near));

        Page<PlaceSummary> result = service.listNearby(USER_ID, 10L, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(PlaceSummary::placeId).containsExactly(3L, 2L);
    }

    @Test
    @DisplayName("dayNumber를 생략하면 일정 전체 장소를 기준으로 한다")
    void dayNumber_생략시_전체기준() {
        Place day1Place = place(1L, 37.500, 127.000);
        Place day2Place = place(2L, 37.600, 127.000); // day1 기준으로는 반경 밖
        Place nearDay2 = place(3L, 37.601, 127.000);  // day2Place 기준으로만 반경 안

        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of(
                tripPlace(101L, (short) 1, day1Place), tripPlace(102L, (short) 2, day2Place)));
        given(placeRepository.findByLatBetweenAndLngBetween(any(), any(), any(), any()))
                .willReturn(List.of(day1Place, day2Place, nearDay2));

        Page<PlaceSummary> result = service.listNearby(USER_ID, 10L, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(PlaceSummary::placeId).containsExactly(3L);
    }
}
