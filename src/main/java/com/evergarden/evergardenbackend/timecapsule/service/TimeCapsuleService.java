package com.evergarden.evergardenbackend.timecapsule.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.media.dto.MediaResponse;
import com.evergarden.evergardenbackend.media.entity.Media;
import com.evergarden.evergardenbackend.media.repository.MediaRepository;
import com.evergarden.evergardenbackend.media.service.MediaMapper;
import com.evergarden.evergardenbackend.timecapsule.dto.TimeCapsuleCreateRequest;
import com.evergarden.evergardenbackend.timecapsule.dto.TimeCapsuleDetail;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsule;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsuleMedia;
import com.evergarden.evergardenbackend.timecapsule.repository.TimeCapsuleMediaRepository;
import com.evergarden.evergardenbackend.timecapsule.repository.TimeCapsuleRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 타임캡슐(TC-01 이하). */
@Service
@RequiredArgsConstructor
@Transactional
public class TimeCapsuleService {

    private final TimeCapsuleRepository timeCapsuleRepository;
    private final TimeCapsuleMediaRepository timeCapsuleMediaRepository;
    private final MediaRepository mediaRepository;
    private final UserRepository userRepository;
    private final TimeCapsuleAccessGuard accessGuard;
    private final TimeCapsuleMapper timeCapsuleMapper;
    private final MediaMapper mediaMapper;

    /**
     * 글과 사진을 담아 봉인한다(TC-01). 만든 직후 상태는 항상 {@code SEALED}라
     * {@code content}·{@code media}는 이 응답에도 담기지 않는다 — 본인도 조건이
     * 충족되기 전에는 내용을 볼 수 없다.
     */
    public TimeCapsuleDetail create(Long userId, TimeCapsuleCreateRequest request) {
        List<Media> media = resolveMedia(userId, request.mediaIds());

        TimeCapsule capsule = buildCapsule(userId, request);
        timeCapsuleRepository.save(capsule);

        List<TimeCapsuleMedia> items = new ArrayList<>();
        for (int i = 0; i < media.size(); i++) {
            items.add(new TimeCapsuleMedia(capsule, media.get(i), (short) (i + 1)));
        }
        timeCapsuleMediaRepository.saveAll(items);

        return timeCapsuleMapper.toDetail(capsule, null, List.of());
    }

    /**
     * 해제 조건 확인·내용 다시 보기(TC-03·06). 소유자만 볼 수 있다 — 게시물과 달리
     * 타임캡슐은 남에게 공개되는 자원이 아니다.
     */
    @Transactional(readOnly = true)
    public TimeCapsuleDetail get(Long userId, Long capsuleId) {
        TimeCapsule capsule = findCapsule(capsuleId);
        accessGuard.checkOwner(capsule, userId);
        return toDetail(capsule);
    }

    /**
     * {@code OPENED}일 때만 실제로 사진·영상을 조회한다 — 봉인 상태에서 미리 불러올
     * 이유가 없다. {@code thumbnailUrl}도 같은 기준으로, 열어본 캡슐의 첫 번째 사진에서
     * 뽑는다({@code TripMapper.thumbnailUrl()}과 같은 "썸네일 없으면 원본" 우선순위).
     */
    private TimeCapsuleDetail toDetail(TimeCapsule capsule) {
        if (!capsule.isOpened()) {
            return timeCapsuleMapper.toDetail(capsule, null, List.of());
        }
        List<TimeCapsuleMedia> items = timeCapsuleMediaRepository.findByCapsuleOrderBySortOrderAsc(capsule);
        List<MediaResponse> media = items.stream().map(item -> mediaMapper.toResponse(item.getMedia())).toList();
        String thumbnailUrl = media.isEmpty() ? null
                : media.get(0).thumbnailUrl() != null ? media.get(0).thumbnailUrl() : media.get(0).url();
        return timeCapsuleMapper.toDetail(capsule, thumbnailUrl, media);
    }

    /**
     * 타임캡슐을 지운다(TC-07). 상태 전환이 아니라 실제로 지운다(ADR-007의 예외) —
     * 본인만 걸린 데이터라서다. 담긴 사진·영상(`media`)은 다른 곳에서도 쓸 수 있어
     * 같이 안 지운다 — {@code time_capsule_media}만 DB의 {@code ON DELETE CASCADE}로
     * 함께 지워지고(`V1__init.sql`의 `tcm_capsule_fk`), {@code media} 테이블은 그대로다.
     * 봉인 상태에서도 지울 수 있다 — 상태를 안 가린다.
     */
    public void delete(Long userId, Long capsuleId) {
        TimeCapsule capsule = findCapsule(capsuleId);
        accessGuard.checkOwner(capsule, userId);
        timeCapsuleRepository.delete(capsule);
    }

    private TimeCapsule findCapsule(Long capsuleId) {
        return timeCapsuleRepository.findById(capsuleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TIME_CAPSULE_NOT_FOUND));
    }

    private TimeCapsule buildCapsule(Long userId, TimeCapsuleCreateRequest request) {
        User owner = userRepository.getReferenceById(userId);
        return switch (request.unlockType()) {
            case DATE -> {
                if (request.unlockDate() == null) {
                    throw new BusinessException(ErrorCode.INVALID_UNLOCK_CONDITION);
                }
                if (!request.unlockDate().isAfter(LocalDate.now())) {
                    throw new BusinessException(ErrorCode.INVALID_REQUEST);
                }
                yield TimeCapsule.sealUntilDate(owner, request.title(), request.content(), request.unlockDate());
            }
            case LOCATION -> {
                if (request.unlockLat() == null || request.unlockLng() == null
                        || request.unlockRadiusMeters() == null) {
                    throw new BusinessException(ErrorCode.INVALID_UNLOCK_CONDITION);
                }
                yield TimeCapsule.sealAtPlace(owner, request.title(), request.content(),
                        BigDecimal.valueOf(request.unlockLat()), BigDecimal.valueOf(request.unlockLng()),
                        request.unlockRadiusMeters(), request.placeName());
            }
        };
    }

    /**
     * {@code READY} 상태가 아니거나 내가 올린 게 아니면 {@code MEDIA_NOT_FOUND}다 — 명세에
     * {@code createTimeCapsule}의 403이 없어서, 남의 미디어라는 걸 굳이 알려주지 않고
     * "없는 것"과 똑같이 취급한다({@code ArchiveItemService}가 이 경우 403을 따로 쓰는 것과 다르다).
     */
    private List<Media> resolveMedia(Long userId, List<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Media> byId = mediaRepository.findAllById(mediaIds).stream()
                .collect(Collectors.toMap(Media::getId, m -> m));
        List<Media> media = new ArrayList<>();
        for (Long mediaId : mediaIds) {
            Media found = byId.get(mediaId);
            if (found == null || !found.isReady() || !found.isUploadedBy(userId)) {
                throw new BusinessException(ErrorCode.MEDIA_NOT_FOUND);
            }
            media.add(found);
        }
        return media;
    }
}
