package com.evergarden.evergardenbackend.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import com.evergarden.evergardenbackend.trip.dto.TripCreateRequest;
import com.evergarden.evergardenbackend.trip.dto.TripDetail;
import com.evergarden.evergardenbackend.trip.dto.TripSummary;
import com.evergarden.evergardenbackend.trip.dto.TripUpdateRequest;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import com.evergarden.evergardenbackend.trip.repository.TripPlaceRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRegionRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/** 일정 기본 CRUD의 검증 순서·오류 코드를 확인한다(PLAN-01·03·04·05·11). */
class TripServiceTest {

    private static final Long USER_ID = 1L;

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripPlaceRepository tripPlaceRepository = mock(TripPlaceRepository.class);
    private final TripRegionRepository tripRegionRepository = mock(TripRegionRepository.class);
    private final RegionRepository regionRepository = mock(RegionRepository.class);
    private final ArchiveRepository archiveRepository = mock(ArchiveRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TripAccessGuard accessGuard = mock(TripAccessGuard.class);
    private final TripMapper tripMapper = mock(TripMapper.class);

    private final TripService tripService = new TripService(
            tripRepository, tripPlaceRepository, tripRegionRepository, regionRepository,
            archiveRepository, userRepository, accessGuard, tripMapper);

    @BeforeEach
    void setUp() {
        User user = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.getReferenceById(USER_ID)).willReturn(user);
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(any())).willReturn(List.of());
        given(tripRegionRepository.findByTrip(any())).willReturn(List.of());
        given(archiveRepository.findByTrip_Id(any())).willReturn(Optional.empty());
        given(tripMapper.toDetail(any(), any(), any(), any())).willReturn(mock(TripDetail.class));
        given(tripMapper.toSummary(any(), any(), any(), any())).willReturn(mock(TripSummary.class));
    }

    private Trip trip(Long ownerId, LocalDate start, LocalDate end) {
        User owner = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(owner, "id", ownerId);
        Trip trip = Trip.builder().owner(owner).title("제주 여행").startDate(start).endDate(end).build();
        ReflectionTestUtils.setField(trip, "id", 10L);
        return trip;
    }

    private Region region(String code) {
        return Region.builder().code(code).level(RegionLevel.SIDO).name("제주")
                .centerLat(java.math.BigDecimal.ZERO).centerLng(java.math.BigDecimal.ZERO)
                .syncedAt(java.time.LocalDateTime.now()).build();
    }

    // ── 생성 ─────────────────────────────────────────────

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 INVALID_DATE_RANGE")
    void 생성_기간역순() {
        TripCreateRequest request = new TripCreateRequest(
                "제주", LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1), List.of("50"));

        assertThatThrownBy(() -> tripService.create(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DATE_RANGE);
        verify(tripRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 지역 코드면 REGION_NOT_FOUND")
    void 생성_없는지역() {
        given(regionRepository.findById("999")).willReturn(Optional.empty());
        TripCreateRequest request = new TripCreateRequest(
                "제주", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), List.of("999"));

        assertThatThrownBy(() -> tripService.create(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGION_NOT_FOUND);
    }

    @Test
    @DisplayName("정상 생성은 여행지를 연결하고 상세를 돌려준다")
    void 생성_정상() {
        given(regionRepository.findById("50")).willReturn(Optional.of(region("50")));
        TripCreateRequest request = new TripCreateRequest(
                "제주", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), List.of("50"));

        TripDetail result = tripService.create(USER_ID, request);

        assertThat(result).isNotNull();
        verify(tripRepository).save(any(Trip.class));
        verify(tripRegionRepository).save(any());
    }

    // ── 조회 ─────────────────────────────────────────────

    @Test
    @DisplayName("없는 일정 조회는 TRIP_NOT_FOUND")
    void 조회_없음() {
        given(tripRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.get(USER_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRIP_NOT_FOUND);
    }

    @Test
    @DisplayName("조회는 소유자 확인을 거친다")
    void 조회_소유자확인() {
        Trip trip = trip(USER_ID, LocalDate.now(), LocalDate.now().plusDays(1));
        given(tripRepository.findById(10L)).willReturn(Optional.of(trip));

        tripService.get(USER_ID, 10L);

        verify(accessGuard).checkOwner(trip, USER_ID);
    }

    // ── 수정 ─────────────────────────────────────────────

    @Test
    @DisplayName("빈 수정 요청은 INVALID_REQUEST")
    void 수정_빈요청() {
        assertThatThrownBy(() -> tripService.update(USER_ID, 10L, new TripUpdateRequest(null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("바뀐 기간의 시작일이 종료일보다 늦으면 INVALID_DATE_RANGE")
    void 수정_기간역순() {
        Trip trip = trip(USER_ID, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10));
        given(tripRepository.findById(10L)).willReturn(Optional.of(trip));

        TripUpdateRequest request = new TripUpdateRequest(null, LocalDate.of(2026, 1, 20), null, null);

        assertThatThrownBy(() -> tripService.update(USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DATE_RANGE);
    }

    @Test
    @DisplayName("기간을 줄여 갈 곳 없는 장소가 생기면 INVALID_REQUEST, details에 밀려나는 날을 담는다(ADR-041)")
    void 수정_기간축소_장소밀려남() {
        Trip trip = trip(USER_ID, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10));
        given(tripRepository.findById(10L)).willReturn(Optional.of(trip));

        TripPlace place = mock(TripPlace.class);
        given(place.getDayNumber()).willReturn((short) 5);
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of(place));

        TripUpdateRequest request = new TripUpdateRequest(null, null, LocalDate.of(2026, 1, 3), null);

        assertThatThrownBy(() -> tripService.update(USER_ID, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getDetails()).containsKey("displacedDays");
                });
    }

    @Test
    @DisplayName("여행지만 바꾸면 기존 연결을 지우고 새로 연결한다")
    void 수정_여행지_교체() {
        Trip trip = trip(USER_ID, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));
        given(tripRepository.findById(10L)).willReturn(Optional.of(trip));
        given(regionRepository.findById("26")).willReturn(Optional.of(region("26")));

        tripService.update(USER_ID, 10L, new TripUpdateRequest(null, null, null, List.of("26")));

        verify(tripRegionRepository).deleteAll(any());
        verify(tripRegionRepository).save(any());
    }

    // ── 삭제 ─────────────────────────────────────────────

    @Test
    @DisplayName("삭제는 소유자 확인 후 지운다")
    void 삭제() {
        Trip trip = trip(USER_ID, LocalDate.now(), LocalDate.now().plusDays(1));
        given(tripRepository.findById(10L)).willReturn(Optional.of(trip));

        tripService.delete(USER_ID, 10L);

        verify(accessGuard).checkOwner(trip, USER_ID);
        verify(tripRepository).delete(trip);
    }

    // ── 목록 정렬(ADR-040) ───────────────────────────────

    @Test
    @DisplayName("예정 여행이 먼저, 지난 여행은 그 아래에 최신순으로 붙는다")
    void 목록_정렬() {
        LocalDate today = LocalDate.now();
        Trip past1 = trip(USER_ID, today.minusDays(20), today.minusDays(15));
        Trip past2 = trip(USER_ID, today.minusDays(10), today.minusDays(5));
        Trip upcoming1 = trip(USER_ID, today.plusDays(10), today.plusDays(12));
        Trip upcoming2 = trip(USER_ID, today.plusDays(1), today.plusDays(3));
        given(tripRepository.findByOwner_Id(USER_ID)).willReturn(List.of(past1, past2, upcoming1, upcoming2));

        tripService.list(USER_ID, PageRequest.of(0, 20));

        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripMapper, org.mockito.Mockito.times(4)).toSummary(captor.capture(), any(), any(), any());
        List<Trip> order = captor.getAllValues();

        assertThat(order).containsExactly(upcoming2, upcoming1, past2, past1);
    }
}
