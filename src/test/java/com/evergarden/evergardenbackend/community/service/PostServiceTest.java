package com.evergarden.evergardenbackend.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveTheme;
import com.evergarden.evergardenbackend.archive.repository.ArchiveItemRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.archive.service.ArchiveAccessGuard;
import com.evergarden.evergardenbackend.archive.service.ArchiveMapper;
import com.evergarden.evergardenbackend.community.dto.PostCreateRequest;
import com.evergarden.evergardenbackend.community.dto.PostDetail;
import com.evergarden.evergardenbackend.community.entity.PostRegion;
import com.evergarden.evergardenbackend.community.entity.RegionSource;
import com.evergarden.evergardenbackend.community.entity.ShareType;
import com.evergarden.evergardenbackend.community.repository.PostRegionRepository;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.media.service.MediaMapper;
import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import com.evergarden.evergardenbackend.trip.entity.TripRegion;
import com.evergarden.evergardenbackend.trip.repository.TripPlaceRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRegionRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import com.evergarden.evergardenbackend.trip.service.TripAccessGuard;
import com.evergarden.evergardenbackend.trip.service.TripMapper;
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

/** 게시물 작성(COMM-04)의 공유 대상·지역 스냅샷 검증 순서를 확인한다. */
class PostServiceTest {

    private static final Long USER_ID = 1L;

    private final PostRepository postRepository = mock(PostRepository.class);
    private final PostRegionRepository postRegionRepository = mock(PostRegionRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final ArchiveRepository archiveRepository = mock(ArchiveRepository.class);
    private final TripRegionRepository tripRegionRepository = mock(TripRegionRepository.class);
    private final TripPlaceRepository tripPlaceRepository = mock(TripPlaceRepository.class);
    private final ArchiveItemRepository archiveItemRepository = mock(ArchiveItemRepository.class);
    private final RegionRepository regionRepository = mock(RegionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TripAccessGuard tripAccessGuard = mock(TripAccessGuard.class);
    private final ArchiveAccessGuard archiveAccessGuard = mock(ArchiveAccessGuard.class);
    private final TripMapper tripMapper = mock(TripMapper.class);
    private final ArchiveMapper archiveMapper = mock(ArchiveMapper.class);
    private final MediaMapper mediaMapper = mock(MediaMapper.class);
    private final PostMapper postMapper = new PostMapper();

    private final PostService postService = new PostService(
            postRepository, postRegionRepository, tripRepository, archiveRepository, tripRegionRepository,
            tripPlaceRepository, archiveItemRepository, regionRepository, userRepository, tripAccessGuard,
            archiveAccessGuard, tripMapper, archiveMapper, postMapper, mediaMapper);

    private User author;

    @BeforeEach
    void setUp() {
        author = user(USER_ID);
        given(userRepository.getReferenceById(USER_ID)).willReturn(author);
        given(archiveRepository.findByTrip_Id(any())).willReturn(Optional.empty());
    }

    private User user(Long id) {
        User user = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Trip trip(Long id) {
        Trip trip = Trip.builder().owner(author).title("제주 여행")
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 1, 3)).build();
        ReflectionTestUtils.setField(trip, "id", id);
        return trip;
    }

    private Archive archive(Long id) {
        Archive archive = Archive.builder().owner(author).title("제주 앨범").theme(ArchiveTheme.POLAROID).build();
        ReflectionTestUtils.setField(archive, "id", id);
        return archive;
    }

    private Region region(String code) {
        return Region.builder().code(code).level(RegionLevel.SIDO).name("지역" + code)
                .centerLat(BigDecimal.ZERO).centerLng(BigDecimal.ZERO).syncedAt(LocalDateTime.now()).build();
    }

    private TripPlace tripPlace(Trip trip, Region region) {
        Place place = Place.builder().contentId("c1").contentTypeId("12").title("장소")
                .lat(BigDecimal.ONE).lng(BigDecimal.ONE).region(region).build();
        return TripPlace.builder().trip(trip).place(place).dayNumber((short) 1).sortOrder((short) 1).build();
    }

    // ── 공유 대상 검증 ───────────────────────────────────────

    @Test
    @DisplayName("코스도 아카이브도 없으면 INVALID_SHARE_TARGET")
    void 공유대상_둘다없음() {
        PostCreateRequest request = new PostCreateRequest("내용", null, null, null);

        assertThatThrownBy(() -> postService.create(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_SHARE_TARGET);
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 코스를 공유하면 TRIP_NOT_FOUND")
    void 없는_코스() {
        given(tripRepository.findById(99L)).willReturn(Optional.empty());
        PostCreateRequest request = new PostCreateRequest("내용", 99L, null, null);

        assertThatThrownBy(() -> postService.create(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRIP_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 코스를 공유하면 403 — 트립 접근가드에 위임한다")
    void 남의_코스() {
        Trip trip = trip(10L);
        given(tripRepository.findById(10L)).willReturn(Optional.of(trip));
        doThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER))
                .when(tripAccessGuard).checkOwner(trip, USER_ID);
        PostCreateRequest request = new PostCreateRequest("내용", 10L, null, null);

        assertThatThrownBy(() -> postService.create(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("없는 아카이브를 공유하면 ARCHIVE_NOT_FOUND")
    void 없는_아카이브() {
        given(archiveRepository.findById(99L)).willReturn(Optional.empty());
        PostCreateRequest request = new PostCreateRequest("내용", null, 99L, List.of("11"));

        assertThatThrownBy(() -> postService.create(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 아카이브를 공유하면 403 — 아카이브 접근가드에 위임한다")
    void 남의_아카이브() {
        Archive archive = archive(20L);
        given(archiveRepository.findById(20L)).willReturn(Optional.of(archive));
        doThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER))
                .when(archiveAccessGuard).checkOwner(archive, USER_ID);
        PostCreateRequest request = new PostCreateRequest("내용", null, 20L, List.of("11"));

        assertThatThrownBy(() -> postService.create(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    // ── 지역 스냅샷 ─────────────────────────────────────────

    @Test
    @DisplayName("코스 없이 아카이브만 공유하면서 regionCodes가 없으면 REGION_REQUIRED")
    void 아카이브만_지역없음() {
        Archive archive = archive(20L);
        given(archiveRepository.findById(20L)).willReturn(Optional.of(archive));
        PostCreateRequest request = new PostCreateRequest("내용", null, 20L, List.of());

        assertThatThrownBy(() -> postService.create(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGION_REQUIRED);
    }

    @Test
    @DisplayName("존재하지 않는 regionCode는 REGION_NOT_FOUND")
    void 아카이브만_없는지역() {
        Archive archive = archive(20L);
        given(archiveRepository.findById(20L)).willReturn(Optional.of(archive));
        given(regionRepository.findById("99")).willReturn(Optional.empty());
        PostCreateRequest request = new PostCreateRequest("내용", null, 20L, List.of("99"));

        assertThatThrownBy(() -> postService.create(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGION_NOT_FOUND);
    }

    @Test
    @DisplayName("코스만 공유하면 여행지와 담긴 장소의 지역을 자동으로 합쳐 COURSE로 저장한다")
    void 코스만_공유_지역자동합산() {
        Trip trip = trip(10L);
        Region tripRegion = region("11");
        Region placeRegionSame = region("11");
        Region placeRegionNew = region("26");
        given(tripRepository.findById(10L)).willReturn(Optional.of(trip));
        given(tripRegionRepository.findByTrip(trip)).willReturn(List.of(new TripRegion(trip, tripRegion)));
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of(
                tripPlace(trip, placeRegionSame), tripPlace(trip, placeRegionNew)));
        given(tripMapper.toSummary(any(), any(), any(), any())).willReturn(mock(com.evergarden.evergardenbackend.trip.dto.TripSummary.class));

        PostCreateRequest request = new PostCreateRequest("코스 공유", 10L, null, null);
        PostDetail result = postService.create(USER_ID, request);

        assertThat(result.regions()).extracting("code").containsExactlyInAnyOrder("11", "26");
        assertThat(result.shareType()).isEqualTo(ShareType.COURSE);
        assertThat(result.sharedArchive()).isNull();

        ArgumentCaptor<List<PostRegion>> captor = ArgumentCaptor.forClass(List.class);
        verify(postRegionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2)
                .allMatch(pr -> pr.getSource() == RegionSource.COURSE);
    }

    @Test
    @DisplayName("아카이브만 공유하면 보낸 regionCodes 그대로 MANUAL로 저장한다")
    void 아카이브만_공유_정상() {
        Archive archive = archive(20L);
        Region region = region("11");
        given(archiveRepository.findById(20L)).willReturn(Optional.of(archive));
        given(regionRepository.findById("11")).willReturn(Optional.of(region));
        given(archiveItemRepository.countByArchive(archive)).willReturn(0L);
        given(archiveMapper.toSummary(any(), anyInt(), any()))
                .willReturn(mock(com.evergarden.evergardenbackend.archive.dto.ArchiveSummary.class));

        PostCreateRequest request = new PostCreateRequest("아카이브 공유", null, 20L, List.of("11"));
        PostDetail result = postService.create(USER_ID, request);

        assertThat(result.shareType()).isEqualTo(ShareType.ARCHIVE);
        assertThat(result.sharedCourse()).isNull();
        assertThat(result.regions()).extracting("code").containsExactly("11");

        ArgumentCaptor<List<PostRegion>> captor = ArgumentCaptor.forClass(List.class);
        verify(postRegionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).allMatch(pr -> pr.getSource() == RegionSource.MANUAL);
    }

    @Test
    @DisplayName("코스와 아카이브를 둘 다 공유하면 shareType이 BOTH다")
    void 둘다_공유() {
        Trip trip = trip(10L);
        Archive archive = archive(20L);
        given(tripRepository.findById(10L)).willReturn(Optional.of(trip));
        given(archiveRepository.findById(20L)).willReturn(Optional.of(archive));
        given(tripRegionRepository.findByTrip(trip)).willReturn(List.of(new TripRegion(trip, region("11"))));
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of());
        given(archiveItemRepository.countByArchive(archive)).willReturn(0L);
        given(tripMapper.toSummary(any(), any(), any(), any())).willReturn(mock(com.evergarden.evergardenbackend.trip.dto.TripSummary.class));
        given(archiveMapper.toSummary(any(), anyInt(), any()))
                .willReturn(mock(com.evergarden.evergardenbackend.archive.dto.ArchiveSummary.class));

        PostCreateRequest request = new PostCreateRequest("둘 다 공유", 10L, 20L, null);
        PostDetail result = postService.create(USER_ID, request);

        assertThat(result.shareType()).isEqualTo(ShareType.BOTH);
        assertThat(result.sharedCourse()).isNotNull();
        assertThat(result.sharedArchive()).isNotNull();
    }
}
