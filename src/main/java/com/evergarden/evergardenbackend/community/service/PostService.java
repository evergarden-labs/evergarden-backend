package com.evergarden.evergardenbackend.community.service;

import com.evergarden.evergardenbackend.archive.dto.ArchiveSummary;
import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveItem;
import com.evergarden.evergardenbackend.archive.repository.ArchiveItemRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.archive.service.ArchiveAccessGuard;
import com.evergarden.evergardenbackend.archive.service.ArchiveMapper;
import com.evergarden.evergardenbackend.community.dto.LikeResult;
import com.evergarden.evergardenbackend.community.dto.PostCreateRequest;
import com.evergarden.evergardenbackend.community.dto.PostDetail;
import com.evergarden.evergardenbackend.community.dto.PostSummary;
import com.evergarden.evergardenbackend.community.dto.PostUpdateRequest;
import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.PostLike;
import com.evergarden.evergardenbackend.community.entity.PostLikeId;
import com.evergarden.evergardenbackend.community.entity.PostRegion;
import com.evergarden.evergardenbackend.community.entity.PostStatus;
import com.evergarden.evergardenbackend.community.entity.RegionSource;
import com.evergarden.evergardenbackend.community.entity.ShareType;
import com.evergarden.evergardenbackend.community.repository.PostLikeRepository;
import com.evergarden.evergardenbackend.community.repository.PostRegionRepository;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.media.dto.MediaResponse;
import com.evergarden.evergardenbackend.media.service.MediaMapper;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import com.evergarden.evergardenbackend.trip.dto.TripSummary;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import com.evergarden.evergardenbackend.trip.entity.TripRegion;
import com.evergarden.evergardenbackend.trip.repository.TripPlaceRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRegionRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import com.evergarden.evergardenbackend.trip.service.TripAccessGuard;
import com.evergarden.evergardenbackend.trip.service.TripMapper;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 커뮤니티 게시물(COMM-01 이하). */
@Service
@RequiredArgsConstructor
@Transactional
public class PostService {

    private final PostRepository postRepository;
    private final PostRegionRepository postRegionRepository;
    private final PostLikeRepository postLikeRepository;
    private final TripRepository tripRepository;
    private final ArchiveRepository archiveRepository;
    private final TripRegionRepository tripRegionRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final ArchiveItemRepository archiveItemRepository;
    private final RegionRepository regionRepository;
    private final UserRepository userRepository;
    private final TripAccessGuard tripAccessGuard;
    private final ArchiveAccessGuard archiveAccessGuard;
    private final PostAccessGuard postAccessGuard;
    private final TripMapper tripMapper;
    private final ArchiveMapper archiveMapper;
    private final PostMapper postMapper;
    private final MediaMapper mediaMapper;

    /**
     * 코스·아카이브·둘 다 중에서 공유할 대상을 골라 게시물을 올린다(COMM-04).
     *
     * <p>지역은 등록 시점에 스냅샷으로 굳힌다(ADR-003) — 코스를 공유하면 일정의 여행지와
     * 담긴 장소들의 지역을 자동으로 합치고, 코스 없이 아카이브만 공유하면
     * {@code regionCodes}를 직접 받는다.
     */
    public PostDetail create(Long userId, PostCreateRequest request) {
        if (request.tripId() == null && request.archiveId() == null) {
            throw new BusinessException(ErrorCode.INVALID_SHARE_TARGET);
        }

        Trip trip = null;
        List<TripPlace> tripPlaces = List.of();
        if (request.tripId() != null) {
            trip = tripRepository.findById(request.tripId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
            tripAccessGuard.checkOwner(trip, userId);
            tripPlaces = tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip);
        }

        Archive archive = null;
        if (request.archiveId() != null) {
            archive = archiveRepository.findById(request.archiveId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ARCHIVE_NOT_FOUND));
            archiveAccessGuard.checkOwner(archive, userId);
        }

        List<Region> regions = resolveRegions(trip, tripPlaces, request.regionCodes());

        Post post = Post.builder()
                .author(userRepository.getReferenceById(userId))
                .content(request.content())
                .shareType(shareType(trip, archive))
                .sharedTrip(trip)
                .sharedArchive(archive)
                .build();
        postRepository.save(post);

        RegionSource source = trip != null ? RegionSource.COURSE : RegionSource.MANUAL;
        postRegionRepository.saveAll(regions.stream().map(region -> new PostRegion(post, region, source)).toList());

        return toDetail(post, regions, trip, tripPlaces, archive, userId);
    }

    /**
     * 게시물 상세를 조회한다(COMM-03·17). 소유자가 아니어도 누구나 볼 수 있다 —
     * {@code viewerId}는 접근 제어가 아니라 {@code likedByMe} 계산용이다.
     *
     * <p>원본 코스·아카이브가 삭제됐어도 게시물 자체는 살아 있으면 {@code 200}이다 —
     * {@code sharedCourse}·{@code sharedArchive}가 {@code null}이 되고 {@code deletedShare}에
     * 무엇이 사라졌는지 담긴다.
     */
    @Transactional(readOnly = true)
    public PostDetail get(Long viewerId, Long postId) {
        Post post = findActivePost(postId);
        return toDetail(post, viewerId);
    }

    /**
     * 게시물 본문만 고친다(COMM-05). 공유 대상과 지역 스냅샷은 바꿀 수 없다(ADR-045) —
     * 여기서 지역을 다시 계산하지 않고 이미 저장된 {@code PostRegion}을 그대로 읽는다.
     */
    public PostDetail update(Long userId, Long postId, PostUpdateRequest request) {
        Post post = findActivePost(postId);
        postAccessGuard.checkOwner(post, userId);

        post.updateContent(request.content());

        return toDetail(post, userId);
    }

    /**
     * 게시물을 지운다(COMM-06). 행을 지우지 않고 {@code status}만 바꾼다(ADR-007) —
     * 댓글이 달린 글을 물리 삭제하면 남의 기록까지 사라진다. 공유했던 코스·아카이브는
     * 건드리지 않는다 — 공유를 거둘 뿐 원본은 그대로다.
     */
    public void delete(Long userId, Long postId) {
        Post post = findActivePost(postId);
        postAccessGuard.checkOwner(post, userId);
        post.delete();
    }

    /**
     * 좋아요를 누른다(COMM-07). 중복 여부를 미리 확인하지 않고 바로 저장을 시도한 뒤
     * {@code (post_id, user_id)} 복합키 위반을 잡아 {@code ALREADY_LIKED}로 바꾼다 —
     * 미리 확인하면 동시 요청 사이에서 새어 나간다(ADR-006).
     */
    public LikeResult like(Long userId, Long postId) {
        Post post = findActivePost(postId);
        try {
            postLikeRepository.saveAndFlush(new PostLike(post, userRepository.getReferenceById(userId)));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_LIKED);
        }
        post.increaseLikeCount();
        return new LikeResult(post.getId(), post.getLikeCount(), true);
    }

    /** 좋아요를 취소한다(COMM-19). */
    public LikeResult unlike(Long userId, Long postId) {
        Post post = findActivePost(postId);
        PostLikeId id = new PostLikeId(postId, userId);
        if (!postLikeRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.NOT_LIKED);
        }
        postLikeRepository.deleteById(id);
        post.decreaseLikeCount();
        return new LikeResult(post.getId(), post.getLikeCount(), false);
    }

    /** 내가 좋아요한 게시물을 최근 순으로(COMM-08). 좋아요한 뒤 삭제된 게시물은 뺀다. */
    @Transactional(readOnly = true)
    public Page<PostSummary> listMyLikedPosts(Long userId, Pageable pageable) {
        return postLikeRepository.findActiveLikedByUser(userId, pageable)
                .map(postLike -> toSummary(postLike.getPost(), userId));
    }

    /** 내가 작성한 게시물을 최신순으로(COMM-09). 삭제한 게시물은 뺀다. */
    @Transactional(readOnly = true)
    public Page<PostSummary> listMyPosts(Long userId, Pageable pageable) {
        return postRepository.findByAuthor_IdAndStatusOrderByCreatedAtDesc(userId, PostStatus.ACTIVE, pageable)
                .map(post -> toSummary(post, userId));
    }

    private Post findActivePost(Long postId) {
        return postRepository.findById(postId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private ShareType shareType(Trip trip, Archive archive) {
        if (trip != null && archive != null) {
            return ShareType.BOTH;
        }
        return trip != null ? ShareType.COURSE : ShareType.ARCHIVE;
    }

    private List<Region> resolveRegions(Trip trip, List<TripPlace> tripPlaces, List<String> regionCodes) {
        if (trip != null) {
            Map<String, Region> byCode = new LinkedHashMap<>();
            for (Region region : tripRegions(trip)) {
                byCode.put(region.getCode(), region);
            }
            for (TripPlace tripPlace : tripPlaces) {
                Region region = tripPlace.getPlace().getRegion();
                byCode.put(region.getCode(), region);
            }
            return List.copyOf(byCode.values());
        }
        if (regionCodes == null || regionCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.REGION_REQUIRED);
        }
        List<Region> regions = new ArrayList<>();
        for (String code : regionCodes) {
            regions.add(regionRepository.findById(code)
                    .orElseThrow(() -> new BusinessException(ErrorCode.REGION_NOT_FOUND)));
        }
        return regions;
    }

    private List<Region> tripRegions(Trip trip) {
        return tripRegionRepository.findByTrip(trip).stream().map(TripRegion::getRegion).toList();
    }

    private PostDetail toDetail(Post post, List<Region> regions, Trip trip, List<TripPlace> tripPlaces,
                                Archive archive, Long viewerId) {
        TripSummary sharedCourse = trip == null ? null
                : tripMapper.toSummary(trip, tripRegions(trip), tripPlaces, linkedArchiveId(trip));
        ArchiveSummary sharedArchive = archive == null ? null
                : archiveMapper.toSummary(archive, (int) archiveItemRepository.countByArchive(archive), viewerId);

        return postMapper.toDetail(post, regions, thumbnailUrl(archive, tripPlaces),
                likedByMe(post, viewerId), sharedCourse, sharedArchive);
    }

    /**
     * 이미 저장된 게시물의 상세를 다시 조립한다. {@code create()}와 달리 트립·아카이브·
     * 지역을 요청이 아니라 {@link Post}에 이미 걸려 있는 연관관계와 {@code post_regions}에서
     * 읽는다 — 수정 시점에 지역을 다시 계산하지 않기 위해서다(ADR-045).
     */
    private PostDetail toDetail(Post post, Long viewerId) {
        Trip trip = post.getSharedTrip();
        Archive archive = post.getSharedArchive();
        List<TripPlace> tripPlaces = trip == null ? List.of()
                : tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip);
        List<Region> regions = postRegionRepository.findByPost(post).stream()
                .map(PostRegion::getRegion).toList();

        return toDetail(post, regions, trip, tripPlaces, archive, viewerId);
    }

    /**
     * 목록에 쓸 요약을 조립한다. {@code listMyLikedPosts} 같은 목록 오퍼레이션이 쓴다.
     *
     * <p>페이지 크기만큼(최대 50건) 항목마다 지역·트립장소·좋아요 여부를 따로 조회한다 —
     * 개인 규모에서는 괜찮지만, 사용자당 게시물이 아주 많아지면 그때 다시 봐야 한다
     * ({@code TripService.list()}와 같은 이유).
     */
    private PostSummary toSummary(Post post, Long viewerId) {
        Trip trip = post.getSharedTrip();
        Archive archive = post.getSharedArchive();
        List<TripPlace> tripPlaces = trip == null ? List.of()
                : tripPlaceRepository.findByTripOrderByDayNumberAscSortOrderAsc(trip);
        List<Region> regions = postRegionRepository.findByPost(post).stream()
                .map(PostRegion::getRegion).toList();

        return postMapper.toSummary(post, regions, thumbnailUrl(archive, tripPlaces), likedByMe(post, viewerId));
    }

    private boolean likedByMe(Post post, Long viewerId) {
        return postLikeRepository.existsById(new PostLikeId(post.getId(), viewerId));
    }

    private Long linkedArchiveId(Trip trip) {
        return archiveRepository.findByTrip_Id(trip.getId()).map(Archive::getId).orElse(null);
    }

    /** 공유한 아카이브의 대표 사진, 없으면 코스 첫 장소의 이미지. */
    private String thumbnailUrl(Archive archive, List<TripPlace> tripPlaces) {
        if (archive != null) {
            String coverImageUrl = archiveCoverImageUrl(archive);
            if (coverImageUrl != null) {
                return coverImageUrl;
            }
        }
        return tripPlaces.isEmpty() ? null : tripPlaces.get(0).getPlace().getThumbnailUrl();
    }

    private String archiveCoverImageUrl(Archive archive) {
        ArchiveItem cover = archive.getCoverItem();
        if (cover == null) {
            return null;
        }
        MediaResponse media = mediaMapper.toResponse(cover.getMedia());
        return media.thumbnailUrl() != null ? media.thumbnailUrl() : media.url();
    }
}
