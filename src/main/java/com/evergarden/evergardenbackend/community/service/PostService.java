package com.evergarden.evergardenbackend.community.service;

import com.evergarden.evergardenbackend.archive.dto.ArchiveSummary;
import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveItem;
import com.evergarden.evergardenbackend.archive.repository.ArchiveItemRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.archive.service.ArchiveAccessGuard;
import com.evergarden.evergardenbackend.archive.service.ArchiveMapper;
import com.evergarden.evergardenbackend.community.dto.PostCreateRequest;
import com.evergarden.evergardenbackend.community.dto.PostDetail;
import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.PostRegion;
import com.evergarden.evergardenbackend.community.entity.RegionSource;
import com.evergarden.evergardenbackend.community.entity.ShareType;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 커뮤니티 게시물(COMM-01 이하). */
@Service
@RequiredArgsConstructor
@Transactional
public class PostService {

    private final PostRepository postRepository;
    private final PostRegionRepository postRegionRepository;
    private final TripRepository tripRepository;
    private final ArchiveRepository archiveRepository;
    private final TripRegionRepository tripRegionRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final ArchiveItemRepository archiveItemRepository;
    private final RegionRepository regionRepository;
    private final UserRepository userRepository;
    private final TripAccessGuard tripAccessGuard;
    private final ArchiveAccessGuard archiveAccessGuard;
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
                                Archive archive, Long userId) {
        TripSummary sharedCourse = trip == null ? null
                : tripMapper.toSummary(trip, tripRegions(trip), tripPlaces, linkedArchiveId(trip));
        ArchiveSummary sharedArchive = archive == null ? null
                : archiveMapper.toSummary(archive, (int) archiveItemRepository.countByArchive(archive), userId);

        return postMapper.toDetail(post, regions, thumbnailUrl(archive, tripPlaces), false, sharedCourse, sharedArchive);
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
