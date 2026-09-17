package com.evergarden.evergardenbackend.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.ShareType;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.trip.dto.ImportCourseRequest;
import com.evergarden.evergardenbackend.trip.dto.TripDetail;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import com.evergarden.evergardenbackend.trip.entity.TripRegion;
import com.evergarden.evergardenbackend.trip.repository.TripPlaceRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRegionRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** 공유된 코스 가져오기(PLAN-12)의 검증 순서와 복제 내용을 확인한다. */
class TripImportServiceTest {

    private static final Long USER_ID = 1L;

    private final PostRepository postRepository = mock(PostRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripRegionRepository tripRegionRepository = mock(TripRegionRepository.class);
    private final TripPlaceRepository tripPlaceRepository = mock(TripPlaceRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TripService tripService = mock(TripService.class);

    private final TripImportService service = new TripImportService(
            postRepository, tripRepository, tripRegionRepository, tripPlaceRepository, userRepository, tripService);

    private Trip original;

    @BeforeEach
    void setUp() {
        User owner = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(owner, "id", USER_ID);
        User author = User.builder().nickname("원작자").build();
        ReflectionTestUtils.setField(author, "id", 2L);

        original = Trip.builder().owner(author).title("원본 코스")
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 1, 3)).build();
        ReflectionTestUtils.setField(original, "id", 100L);

        given(userRepository.getReferenceById(USER_ID)).willReturn(owner);
        given(tripRegionRepository.findByTrip(original)).willReturn(List.of());
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(original)).willReturn(List.of());
        given(tripService.toDetail(any())).willReturn(mock(TripDetail.class));
    }

    private Post post(Long id, ShareType shareType, Trip sharedTrip) {
        Post post = Post.builder().author(original.getOwner()).content("내용").shareType(shareType)
                .sharedTrip(sharedTrip).build();
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    @Test
    @DisplayName("없는 게시물은 POST_NOT_FOUND")
    void 없는_게시물() {
        given(postRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.importCourse(USER_ID, 999L, new ImportCourseRequest(LocalDate.now(), null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 게시물도 POST_NOT_FOUND")
    void 삭제된_게시물() {
        Post deleted = post(1L, ShareType.COURSE, original);
        deleted.delete();
        given(postRepository.findById(1L)).willReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.importCourse(USER_ID, 1L, new ImportCourseRequest(LocalDate.now(), null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("공유된 코스가 없으면(아카이브만 공유) TRIP_NOT_FOUND")
    void 공유된_코스_없음() {
        Post archiveOnly = post(1L, ShareType.ARCHIVE, null);
        given(postRepository.findById(1L)).willReturn(Optional.of(archiveOnly));

        assertThatThrownBy(() -> service.importCourse(USER_ID, 1L, new ImportCourseRequest(LocalDate.now(), null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRIP_NOT_FOUND);
    }

    @Test
    @DisplayName("원본 코스가 삭제됐으면(FK가 null로 비워짐) TRIP_NOT_FOUND")
    void 원본_삭제됨() {
        Post orphaned = post(1L, ShareType.COURSE, null);
        given(postRepository.findById(1L)).willReturn(Optional.of(orphaned));

        assertThatThrownBy(() -> service.importCourse(USER_ID, 1L, new ImportCourseRequest(LocalDate.now(), null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRIP_NOT_FOUND);
    }

    @Test
    @DisplayName("startDate를 생략하면 INVALID_REQUEST")
    void startDate_생략() {
        Post p = post(1L, ShareType.COURSE, original);
        given(postRepository.findById(1L)).willReturn(Optional.of(p));

        assertThatThrownBy(() -> service.importCourse(USER_ID, 1L, new ImportCourseRequest(null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
        verify(tripRepository, never()).save(any());
    }

    @Test
    @DisplayName("본문 자체가 없으면 INVALID_REQUEST")
    void 본문_없음() {
        Post p = post(1L, ShareType.COURSE, original);
        given(postRepository.findById(1L)).willReturn(Optional.of(p));

        assertThatThrownBy(() -> service.importCourse(USER_ID, 1L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("제목을 생략하면 원본 제목을 그대로 쓴다")
    void 제목_생략시_원본제목() {
        Post p = post(1L, ShareType.COURSE, original);
        given(postRepository.findById(1L)).willReturn(Optional.of(p));

        service.importCourse(USER_ID, 1L, new ImportCourseRequest(LocalDate.of(2026, 5, 1), null));

        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("원본 코스");
    }

    @Test
    @DisplayName("startDate부터 원본 일수만큼 기간을 다시 매기고, originTrip을 남긴다(ADR-005·048)")
    void 기간_재계산과_원본연결() {
        Post p = post(1L, ShareType.COURSE, original);
        given(postRepository.findById(1L)).willReturn(Optional.of(p));

        service.importCourse(USER_ID, 1L, new ImportCourseRequest(LocalDate.of(2026, 5, 1), "내 코스"));

        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());
        Trip copy = captor.getValue();
        assertThat(copy.getTitle()).isEqualTo("내 코스");
        assertThat(copy.getStartDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(copy.getEndDate()).isEqualTo(LocalDate.of(2026, 5, 3)); // 원본 3일짜리
        assertThat(copy.getOriginTrip()).isSameAs(original);
        assertThat(copy.isOwnedBy(USER_ID)).isTrue();
    }

    @Test
    @DisplayName("원본의 지역·장소를 복제한다 — 원본을 참조하지 않고 새 행으로 만든다")
    void 지역과_장소_복제() {
        Post p = post(1L, ShareType.COURSE, original);
        given(postRepository.findById(1L)).willReturn(Optional.of(p));

        Region region = Region.builder().code("11").level(RegionLevel.SIDO).name("서울")
                .centerLat(BigDecimal.ZERO).centerLng(BigDecimal.ZERO).syncedAt(LocalDateTime.now()).build();
        given(tripRegionRepository.findByTrip(original)).willReturn(List.of(new TripRegion(original, region)));

        Place place = Place.builder().contentId("c1").contentTypeId("12").title("장소1")
                .lat(BigDecimal.ONE).lng(BigDecimal.ONE).region(region).build();
        TripPlace originalPlace = TripPlace.builder().trip(original).place(place)
                .dayNumber((short) 1).sortOrder((short) 1).memo("메모").build();
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(original)).willReturn(List.of(originalPlace));

        service.importCourse(USER_ID, 1L, new ImportCourseRequest(LocalDate.of(2026, 5, 1), null));

        ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(tripCaptor.capture());
        Trip copy = tripCaptor.getValue();

        ArgumentCaptor<List<TripRegion>> regionCaptor = ArgumentCaptor.forClass(List.class);
        verify(tripRegionRepository).saveAll(regionCaptor.capture());
        assertThat(regionCaptor.getValue()).hasSize(1);
        assertThat(regionCaptor.getValue().get(0).getTrip()).isSameAs(copy);
        assertThat(regionCaptor.getValue().get(0).getRegion()).isSameAs(region);

        ArgumentCaptor<List<TripPlace>> placeCaptor = ArgumentCaptor.forClass(List.class);
        verify(tripPlaceRepository).saveAll(placeCaptor.capture());
        TripPlace copiedPlace = placeCaptor.getValue().get(0);
        assertThat(copiedPlace.getTrip()).isSameAs(copy);
        assertThat(copiedPlace.getPlace()).isSameAs(place);
        assertThat(copiedPlace.getDayNumber()).isEqualTo((short) 1);
        assertThat(copiedPlace.getSortOrder()).isEqualTo((short) 1);
        assertThat(copiedPlace.getMemo()).isEqualTo("메모");
    }
}
