package com.evergarden.evergardenbackend.timecapsule.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.media.entity.Media;
import com.evergarden.evergardenbackend.media.repository.MediaRepository;
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
    private final TimeCapsuleMapper timeCapsuleMapper;

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
