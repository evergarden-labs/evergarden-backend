package com.evergarden.evergardenbackend.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
import com.evergarden.evergardenbackend.community.dto.LikeResult;
import com.evergarden.evergardenbackend.community.dto.PostCreateRequest;
import com.evergarden.evergardenbackend.community.dto.PostDetail;
import com.evergarden.evergardenbackend.community.dto.PostSortType;
import com.evergarden.evergardenbackend.community.dto.PostSummary;
import com.evergarden.evergardenbackend.community.dto.PostUpdateRequest;
import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.PostLike;
import com.evergarden.evergardenbackend.community.entity.PostRegion;
import com.evergarden.evergardenbackend.community.entity.PostStatus;
import com.evergarden.evergardenbackend.community.entity.RegionSource;
import com.evergarden.evergardenbackend.community.entity.ShareType;
import com.evergarden.evergardenbackend.community.repository.PostLikeRepository;
import com.evergarden.evergardenbackend.community.repository.PostRegionRepository;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.response.CursorPage;
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
import org.springframework.data.domain.Page;
import org.springframework.test.util.ReflectionTestUtils;

/** 게시물 작성(COMM-04)의 공유 대상·지역 스냅샷 검증 순서를 확인한다. */
class PostServiceTest {

    private static final Long USER_ID = 1L;

    private final PostRepository postRepository = mock(PostRepository.class);
    private final PostRegionRepository postRegionRepository = mock(PostRegionRepository.class);
    private final PostLikeRepository postLikeRepository = mock(PostLikeRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final ArchiveRepository archiveRepository = mock(ArchiveRepository.class);
    private final TripRegionRepository tripRegionRepository = mock(TripRegionRepository.class);
    private final TripPlaceRepository tripPlaceRepository = mock(TripPlaceRepository.class);
    private final ArchiveItemRepository archiveItemRepository = mock(ArchiveItemRepository.class);
    private final RegionRepository regionRepository = mock(RegionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TripAccessGuard tripAccessGuard = mock(TripAccessGuard.class);
    private final ArchiveAccessGuard archiveAccessGuard = mock(ArchiveAccessGuard.class);
    private final PostAccessGuard postAccessGuard = mock(PostAccessGuard.class);
    private final TripMapper tripMapper = mock(TripMapper.class);
    private final ArchiveMapper archiveMapper = mock(ArchiveMapper.class);
    private final MediaMapper mediaMapper = mock(MediaMapper.class);
    private final PostMapper postMapper = new PostMapper();

    private final PostService postService = new PostService(
            postRepository, postRegionRepository, postLikeRepository, tripRepository, archiveRepository,
            tripRegionRepository, tripPlaceRepository, archiveItemRepository, regionRepository, userRepository,
            tripAccessGuard, archiveAccessGuard, postAccessGuard, tripMapper, archiveMapper, postMapper, mediaMapper);

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

    // ── 조회(COMM-03·17) ─────────────────────────────────────

    @Test
    @DisplayName("없는 게시물 조회는 POST_NOT_FOUND")
    void 조회_없는게시물() {
        given(postRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.get(USER_ID, 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 게시물 조회는 POST_NOT_FOUND — 원본만 삭제된 경우와 다르다")
    void 조회_삭제된게시물() {
        Post post = post(5L);
        post.delete();
        given(postRepository.findById(5L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.get(USER_ID, 5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("정상 조회는 공유된 코스 요약을 함께 돌려준다")
    void 조회_정상_코스포함() {
        Trip trip = trip(10L);
        Post post = Post.builder().author(author).content("내용").shareType(ShareType.COURSE)
                .sharedTrip(trip).build();
        ReflectionTestUtils.setField(post, "id", 5L);
        given(postRepository.findById(5L)).willReturn(Optional.of(post));
        given(postRegionRepository.findByPost(post)).willReturn(List.of());
        given(tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip)).willReturn(List.of());
        given(tripRegionRepository.findByTrip(trip)).willReturn(List.of());
        given(tripMapper.toSummary(any(), any(), any(), any()))
                .willReturn(mock(com.evergarden.evergardenbackend.trip.dto.TripSummary.class));

        PostDetail result = postService.get(USER_ID, 5L);

        assertThat(result.sharedCourse()).isNotNull();
        assertThat(result.deletedShare()).isEmpty();
    }

    @Test
    @DisplayName("원본 코스가 삭제됐어도 게시물은 200이고 deletedShare에 COURSE가 담긴다(COMM-17)")
    void 조회_원본코스삭제됨() {
        Post post = Post.builder().author(author).content("내용").shareType(ShareType.COURSE).build();
        ReflectionTestUtils.setField(post, "id", 5L);
        given(postRepository.findById(5L)).willReturn(Optional.of(post));
        given(postRegionRepository.findByPost(post)).willReturn(List.of());

        PostDetail result = postService.get(USER_ID, 5L);

        assertThat(result.sharedCourse()).isNull();
        assertThat(result.deletedShare()).containsExactly(ShareType.COURSE);
    }

    // ── 수정(COMM-05) ────────────────────────────────────────

    private Post post(Long id) {
        Post post = Post.builder().author(author).content("원래 내용").shareType(ShareType.ARCHIVE).build();
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    @Test
    @DisplayName("없는 게시물을 수정하면 POST_NOT_FOUND")
    void 수정_없는게시물() {
        given(postRepository.findById(99L)).willReturn(Optional.empty());
        PostUpdateRequest request = new PostUpdateRequest("고친 내용");

        assertThatThrownBy(() -> postService.update(USER_ID, 99L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 게시물을 수정하면 POST_NOT_FOUND")
    void 수정_삭제된게시물() {
        Post post = post(5L);
        post.delete();
        given(postRepository.findById(5L)).willReturn(Optional.of(post));
        PostUpdateRequest request = new PostUpdateRequest("고친 내용");

        assertThatThrownBy(() -> postService.update(USER_ID, 5L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 게시물을 수정하면 403 — 게시물 접근가드에 위임한다")
    void 수정_남의게시물() {
        Post post = post(5L);
        given(postRepository.findById(5L)).willReturn(Optional.of(post));
        doThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER))
                .when(postAccessGuard).checkOwner(post, USER_ID);
        PostUpdateRequest request = new PostUpdateRequest("고친 내용");

        assertThatThrownBy(() -> postService.update(USER_ID, 5L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("정상 수정은 본문만 바뀌고, 지역은 재계산 없이 저장된 스냅샷을 그대로 읽는다")
    void 수정_정상() {
        Post post = post(5L);
        Region savedRegion = region("11");
        given(postRepository.findById(5L)).willReturn(Optional.of(post));
        given(postRegionRepository.findByPost(post))
                .willReturn(List.of(new PostRegion(post, savedRegion, RegionSource.MANUAL)));
        PostUpdateRequest request = new PostUpdateRequest("고친 내용");

        PostDetail result = postService.update(USER_ID, 5L, request);

        assertThat(result.content()).isEqualTo("고친 내용");
        assertThat(result.regions()).extracting("code").containsExactly("11");
        verify(tripRepository, never()).findById(any());
        verify(regionRepository, never()).findById(any());
    }

    // ── 삭제(COMM-06) ────────────────────────────────────────

    @Test
    @DisplayName("없는 게시물을 삭제하면 POST_NOT_FOUND")
    void 삭제_없는게시물() {
        given(postRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.delete(USER_ID, 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 삭제된 게시물을 또 삭제하면 POST_NOT_FOUND")
    void 삭제_이미삭제됨() {
        Post post = post(5L);
        post.delete();
        given(postRepository.findById(5L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.delete(USER_ID, 5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 게시물을 삭제하면 403 — 게시물 접근가드에 위임한다")
    void 삭제_남의게시물() {
        Post post = post(5L);
        given(postRepository.findById(5L)).willReturn(Optional.of(post));
        doThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER))
                .when(postAccessGuard).checkOwner(post, USER_ID);

        assertThatThrownBy(() -> postService.delete(USER_ID, 5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("정상 삭제는 행을 지우지 않고 상태만 DELETED로 바꾼다")
    void 삭제_정상() {
        Post post = post(5L);
        given(postRepository.findById(5L)).willReturn(Optional.of(post));

        postService.delete(USER_ID, 5L);

        assertThat(post.isDeleted()).isTrue();
        verify(postRepository, never()).delete(any());
    }

    // ── 좋아요(COMM-07·08·19) ────────────────────────────────

    @Test
    @DisplayName("없는 게시물에 좋아요하면 POST_NOT_FOUND")
    void 좋아요_없는게시물() {
        given(postRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.like(USER_ID, 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 좋아요한 게시물이면 DB 제약 위반을 ALREADY_LIKED로 바꾼다")
    void 좋아요_중복() {
        Post post = post(5L);
        given(postRepository.findById(5L)).willReturn(Optional.of(post));
        given(postLikeRepository.saveAndFlush(any())).willThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> postService.like(USER_ID, 5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_LIKED);
        assertThat(post.getLikeCount()).isZero();
    }

    @Test
    @DisplayName("정상 좋아요는 좋아요 수를 늘리고 likedByMe=true를 돌려준다")
    void 좋아요_정상() {
        Post post = post(5L);
        given(postRepository.findById(5L)).willReturn(Optional.of(post));

        LikeResult result = postService.like(USER_ID, 5L);

        assertThat(result.likeCount()).isEqualTo(1);
        assertThat(result.likedByMe()).isTrue();
        assertThat(post.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요하지 않은 게시물을 취소하면 NOT_LIKED")
    void 좋아요취소_안한것() {
        Post post = post(5L);
        given(postRepository.findById(5L)).willReturn(Optional.of(post));
        given(postLikeRepository.existsById(any())).willReturn(false);

        assertThatThrownBy(() -> postService.unlike(USER_ID, 5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_LIKED);
    }

    @Test
    @DisplayName("정상 취소는 좋아요 수를 줄이고 likedByMe=false를 돌려준다")
    void 좋아요취소_정상() {
        Post post = post(5L);
        ReflectionTestUtils.setField(post, "likeCount", 1);
        given(postRepository.findById(5L)).willReturn(Optional.of(post));
        given(postLikeRepository.existsById(any())).willReturn(true);

        LikeResult result = postService.unlike(USER_ID, 5L);

        assertThat(result.likeCount()).isZero();
        assertThat(result.likedByMe()).isFalse();
        verify(postLikeRepository).deleteById(any());
    }

    @Test
    @DisplayName("내 좋아요 목록은 최근 순으로 요약을 돌려준다")
    void 좋아요목록_조회() {
        Post post = post(5L);
        PostLike postLike = new PostLike(post, author);
        given(postLikeRepository.findActiveLikedByUser(eq(USER_ID), any()))
                .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(postLike)));
        given(postRegionRepository.findByPost(post)).willReturn(List.of());

        Page<PostSummary> result = postService.listMyLikedPosts(USER_ID, org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).postId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("내 게시물 목록은 최신순으로 요약을 돌려준다")
    void 내게시물목록_조회() {
        Post post = post(5L);
        given(postRepository.findByAuthor_IdAndStatusOrderByCreatedAtDesc(eq(USER_ID), eq(PostStatus.ACTIVE), any()))
                .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(post)));
        given(postRegionRepository.findByPost(post)).willReturn(List.of());

        Page<PostSummary> result = postService.listMyPosts(USER_ID, org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).postId()).isEqualTo(5L);
    }

    // ── 피드 조회(COMM-01·02) ────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 지역의 피드를 조회하면 REGION_NOT_FOUND")
    void 지역피드_없는지역() {
        given(regionRepository.findById("999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.listRegionPosts(USER_ID, "999", null, null, 20))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGION_NOT_FOUND);
    }

    @Test
    @DisplayName("sort를 안 보내면 최신순(regionCode 없이)으로 조회한다")
    void 전체피드_기본값은_최신순() {
        Post post = post(5L);
        given(postRepository.findLatest(eq(null), eq(null), any())).willReturn(List.of(post));
        given(postRegionRepository.findByPost(post)).willReturn(List.of());

        CursorPage<PostSummary> result = postService.listPosts(USER_ID, null, null, 20);

        assertThat(result.items()).hasSize(1);
        verify(postRepository, never()).findPopular(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("인기순은 최근 30일 이후 게시물만 대상으로 조회한다")
    void 전체피드_인기순은_30일창() {
        given(postRepository.findPopular(any(), any(), any(), any(), any())).willReturn(List.of());

        postService.listPosts(USER_ID, PostSortType.POPULAR, null, 20);

        ArgumentCaptor<java.time.LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(java.time.LocalDateTime.class);
        verify(postRepository).findPopular(eq(null), sinceCaptor.capture(), eq(null), eq(null), any());
        assertThat(sinceCaptor.getValue()).isCloseTo(
                java.time.LocalDateTime.now().minusDays(30), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("지역별 피드는 regionCode를 그대로 쿼리에 넘긴다")
    void 지역피드_정상() {
        Region region = region("11");
        given(regionRepository.findById("11")).willReturn(Optional.of(region));
        given(postRepository.findLatest(eq("11"), eq(null), any())).willReturn(List.of());

        postService.listRegionPosts(USER_ID, "11", null, null, 20);

        verify(postRepository).findLatest(eq("11"), eq(null), any());
    }

    @Test
    @DisplayName("최신순은 다음 페이지가 있으면 id 커서를 만든다")
    void 전체피드_최신순_다음페이지() {
        List<Post> twentyOne = java.util.stream.LongStream.rangeClosed(1, 21)
                .mapToObj(this::post).toList();
        given(postRepository.findLatest(eq(null), eq(null), any())).willReturn(twentyOne);
        twentyOne.forEach(p -> given(postRegionRepository.findByPost(p)).willReturn(List.of()));

        CursorPage<PostSummary> result = postService.listPosts(USER_ID, null, null, 20);

        assertThat(result.items()).hasSize(20);
        assertThat(result.meta().hasNext()).isTrue();
        assertThat(result.meta().nextCursor()).isNotNull();
    }

    @Test
    @DisplayName("인기순 커서는 좋아요 수와 id를 함께 실어, 다음 조회에 그대로 되돌려준다")
    void 전체피드_인기순_커서왕복() {
        Post post = post(7L);
        ReflectionTestUtils.setField(post, "likeCount", 3);
        given(postRepository.findPopular(any(), any(), eq(null), eq(null), any()))
                .willReturn(List.of(post));
        given(postRegionRepository.findByPost(post)).willReturn(List.of());

        CursorPage<PostSummary> first = postService.listPosts(USER_ID, PostSortType.POPULAR, null, 20);
        assertThat(first.meta().hasNext()).isFalse();

        // 다음 페이지가 있는 상황을 흉내내려면 size+1개를 돌려줘야 하므로, 21개로 재구성
        List<Post> twentyOne = java.util.stream.LongStream.rangeClosed(1, 21)
                .mapToObj(this::post).toList();
        twentyOne.forEach(p -> ReflectionTestUtils.setField(p, "likeCount", 5));
        given(postRepository.findPopular(any(), any(), eq(null), eq(null), any())).willReturn(twentyOne);
        twentyOne.forEach(p -> given(postRegionRepository.findByPost(p)).willReturn(List.of()));

        CursorPage<PostSummary> paged = postService.listPosts(USER_ID, PostSortType.POPULAR, null, 20);
        String cursor = paged.meta().nextCursor();
        assertThat(cursor).isNotNull();

        given(postRepository.findPopular(any(), any(), eq(5), eq(20L), any())).willReturn(List.of());
        postService.listPosts(USER_ID, PostSortType.POPULAR, cursor, 20);

        verify(postRepository).findPopular(eq(null), any(), eq(5), eq(20L), any());
    }

    @Test
    @DisplayName("인기순 조회에 깨진 커서를 보내면 INVALID_REQUEST")
    void 전체피드_인기순_잘못된커서() {
        assertThatThrownBy(() -> postService.listPosts(USER_ID, PostSortType.POPULAR, "@@broken@@", 20))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }
}
