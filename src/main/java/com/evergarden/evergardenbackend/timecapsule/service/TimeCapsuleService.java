package com.evergarden.evergardenbackend.timecapsule.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.response.CursorMeta;
import com.evergarden.evergardenbackend.global.response.CursorPage;
import com.evergarden.evergardenbackend.global.util.CursorCodec;
import com.evergarden.evergardenbackend.media.dto.MediaResponse;
import com.evergarden.evergardenbackend.media.entity.Media;
import com.evergarden.evergardenbackend.media.repository.MediaRepository;
import com.evergarden.evergardenbackend.media.service.MediaMapper;
import com.evergarden.evergardenbackend.notification.entity.NotificationTargetType;
import com.evergarden.evergardenbackend.notification.entity.NotificationType;
import com.evergarden.evergardenbackend.notification.service.NotificationService;
import com.evergarden.evergardenbackend.timecapsule.dto.LocationUnlockCheckRequest;
import com.evergarden.evergardenbackend.timecapsule.dto.TimeCapsuleCreateRequest;
import com.evergarden.evergardenbackend.timecapsule.dto.TimeCapsuleDetail;
import com.evergarden.evergardenbackend.timecapsule.dto.TimeCapsuleSummary;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsule;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsuleMedia;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsuleStatus;
import com.evergarden.evergardenbackend.timecapsule.entity.UnlockType;
import com.evergarden.evergardenbackend.timecapsule.repository.TimeCapsuleMediaRepository;
import com.evergarden.evergardenbackend.timecapsule.repository.TimeCapsuleRepository;
import com.evergarden.evergardenbackend.trip.service.GeoDistance;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
    private final NotificationService notificationService;

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
     * 캡슐을 연다(TC-05). {@code UNLOCKABLE}이어야만 열 수 있다 — 아직 해제 조건이
     * 안 됐으면 {@code CAPSULE_NOT_UNLOCKABLE}(해제 조건 종류를 details에 담는다),
     * 이미 열었으면 대신 {@code GET}을 쓰라는 뜻으로 {@code CAPSULE_ALREADY_OPENED}다.
     */
    public TimeCapsuleDetail open(Long userId, Long capsuleId) {
        TimeCapsule capsule = findCapsule(capsuleId);
        accessGuard.checkOwner(capsule, userId);

        if (capsule.isOpened()) {
            throw new BusinessException(ErrorCode.CAPSULE_ALREADY_OPENED);
        }
        if (!capsule.isUnlockable()) {
            throw new BusinessException(ErrorCode.CAPSULE_NOT_UNLOCKABLE,
                    Map.of("unlockType", capsule.getUnlockType()));
        }
        capsule.open(LocalDateTime.now());
        return toDetail(capsule);
    }

    /**
     * 봉인·해제 함께, 최신순(TC-02). 목록엔 내용을 안 담으니 {@code toSummary()}가
     * {@code toDetail()}보다 훨씬 가볍다 — 열어본 것만 첫 사진을 확인하면 된다.
     */
    @Transactional(readOnly = true)
    public CursorPage<TimeCapsuleSummary> list(Long userId, String cursor, int size) {
        Long cursorId = CursorCodec.decode(cursor);
        List<TimeCapsule> fetched = timeCapsuleRepository.findAllByOwner(userId, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = fetched.size() > size;
        List<TimeCapsule> page = hasNext ? fetched.subList(0, size) : fetched;
        List<TimeCapsuleSummary> summaries = page.stream().map(this::toSummary).toList();

        CursorMeta meta = !hasNext ? CursorMeta.last()
                : CursorMeta.of(CursorCodec.encode(page.get(page.size() - 1).getId()));
        return new CursorPage<>(summaries, meta);
    }

    /** 열어본 것만 최근에 연 순서로(TC-06). */
    @Transactional(readOnly = true)
    public CursorPage<TimeCapsuleSummary> listOpened(Long userId, String cursor, int size) {
        OpenedCursor decoded = decodeOpenedCursor(cursor);
        LocalDateTime cursorOpenedAt = decoded == null ? null : decoded.openedAt();
        Long cursorId = decoded == null ? null : decoded.id();
        List<TimeCapsule> fetched = timeCapsuleRepository.findOpenedByOwner(
                userId, cursorOpenedAt, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = fetched.size() > size;
        List<TimeCapsule> page = hasNext ? fetched.subList(0, size) : fetched;
        List<TimeCapsuleSummary> summaries = page.stream().map(this::toSummary).toList();

        CursorMeta meta = !hasNext ? CursorMeta.last()
                : CursorMeta.of(encodeOpenedCursor(page.get(page.size() - 1)));
        return new CursorPage<>(summaries, meta);
    }

    private TimeCapsuleSummary toSummary(TimeCapsule capsule) {
        return timeCapsuleMapper.toSummary(capsule, capsule.isOpened() ? firstThumbnailUrl(capsule) : null);
    }

    /**
     * 위치 해제 판정(TC-04). 배터리 소모 없이 앱이 켜질 때 한 번 위치를 보고하는
     * 방식이라(ADR-026) 서버가 능동적으로 찾아가지 않는다 — 이 사용자의 아직 안 열린
     * 위치 조건 캡슐만 훑어서, 반경 안에 들어온 것만 {@code UNLOCKABLE}로 바꾸고 알린다.
     * {@code markUnlockable()}이 관리 중인 엔티티를 바꾸므로 트랜잭션 커밋 시 더티 체킹으로
     * 반영된다 — 따로 저장하지 않는다({@code TripService.update()}와 같은 방식).
     */
    public List<TimeCapsuleSummary> checkLocationUnlock(Long userId, LocationUnlockCheckRequest request) {
        BigDecimal currentLat = BigDecimal.valueOf(request.lat());
        BigDecimal currentLng = BigDecimal.valueOf(request.lng());

        List<TimeCapsule> candidates = timeCapsuleRepository.findByOwner_IdAndUnlockTypeAndStatus(
                userId, UnlockType.LOCATION, TimeCapsuleStatus.SEALED);

        List<TimeCapsuleSummary> newlyUnlockable = new ArrayList<>();
        for (TimeCapsule capsule : candidates) {
            long distance = GeoDistance.metersBetween(
                    capsule.getUnlockLat(), capsule.getUnlockLng(), currentLat, currentLng);
            if (distance > capsule.getUnlockRadiusM()) {
                continue;
            }
            capsule.markUnlockable();
            notifyUnlockable(capsule);
            newlyUnlockable.add(toSummary(capsule));
        }
        return newlyUnlockable;
    }

    /**
     * 날짜 해제 배치(TC-04의 날짜 쪽, 스케줄러가 매일 호출). 위치 조건과 달리 클라이언트가
     * 알려줄 게 없어 서버가 스스로 오늘 날짜를 기준으로 판정한다. 사용자별로 나뉘지 않고
     * 전체를 한 번에 훑는다는 점만 {@link #checkLocationUnlock}과 다르다.
     *
     * @return 새로 {@code UNLOCKABLE}이 된 캡슐 수(스케줄러가 로그를 남기는 용도)
     */
    public int unlockDueDateCapsules(LocalDate today) {
        List<TimeCapsule> due = timeCapsuleRepository.findByUnlockTypeAndStatusAndUnlockDateLessThanEqual(
                UnlockType.DATE, TimeCapsuleStatus.SEALED, today);
        for (TimeCapsule capsule : due) {
            capsule.markUnlockable();
            notifyUnlockable(capsule);
        }
        return due.size();
    }

    private void notifyUnlockable(TimeCapsule capsule) {
        notificationService.notify(capsule.getOwner(), NotificationType.CAPSULE_UNLOCK,
                "타임캡슐을 열어볼 수 있어요", "\"" + capsule.getTitle() + "\" 캡슐을 열어볼 수 있게 됐어요.",
                NotificationTargetType.TIME_CAPSULE, capsule.getId());
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

    private String firstThumbnailUrl(TimeCapsule capsule) {
        List<TimeCapsuleMedia> items = timeCapsuleMediaRepository.findByCapsuleOrderBySortOrderAsc(capsule);
        if (items.isEmpty()) {
            return null;
        }
        MediaResponse first = mediaMapper.toResponse(items.get(0).getMedia());
        return first.thumbnailUrl() != null ? first.thumbnailUrl() : first.url();
    }

    /**
     * 해제 기록 커서. {@code (openedAt, id)} 두 값을 같이 실어야 한다 — 정렬 기준과 다른
     * 값 하나만 커서로 쓰면 같은 순간 열린 캡슐들 사이에서 항목이 통째로 빠질 수 있다
     * (인기 피드 커서와 같은 함정, {@code PostService.PopularCursor} 참고).
     */
    private record OpenedCursor(LocalDateTime openedAt, Long id) {
    }

    private String encodeOpenedCursor(TimeCapsule capsule) {
        String raw = capsule.getOpenedAt() + "_" + capsule.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private OpenedCursor decodeOpenedCursor(String cursor) {
        if (cursor == null) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = raw.lastIndexOf('_');
            return new OpenedCursor(LocalDateTime.parse(raw.substring(0, sep)), Long.valueOf(raw.substring(sep + 1)));
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
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
