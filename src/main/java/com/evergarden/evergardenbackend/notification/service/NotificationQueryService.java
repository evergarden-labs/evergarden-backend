package com.evergarden.evergardenbackend.notification.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.notification.dto.NotificationResponse;
import com.evergarden.evergardenbackend.notification.dto.NotificationSettingResponse;
import com.evergarden.evergardenbackend.notification.dto.UnreadCount;
import com.evergarden.evergardenbackend.notification.dto.UpdateNotificationSettingsRequest;
import com.evergarden.evergardenbackend.notification.entity.Notification;
import com.evergarden.evergardenbackend.notification.entity.NotificationSetting;
import com.evergarden.evergardenbackend.notification.entity.NotificationSettingId;
import com.evergarden.evergardenbackend.notification.entity.NotificationType;
import com.evergarden.evergardenbackend.notification.repository.NotificationRepository;
import com.evergarden.evergardenbackend.notification.repository.NotificationSettingRepository;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 조회·읽음 처리·설정(NOTI-01~06). 발송은 {@link NotificationService}가 따로 맡는다 —
 * 타임캡슐·아카이브 초대·관리자 제재 등 다른 도메인이 "알림 하나 보내고 싶을 뿐"인데
 * 조회·읽음·설정까지 딸려오는 넓어진 서비스에 얽히지 않도록 분리했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final UserRepository userRepository;

    /** 받은 알림을 최신순으로(NOTI-01). {@code COLLAB_INVITE}·{@code CAPSULE_UNLOCK}·{@code WARNING}은
     * 전부 이 목록의 {@code type} 값일 뿐 조회 로직은 타입 구분이 없다(NOTI-02·05). */
    public Page<NotificationResponse> listNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByReceiver_IdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponse::of);
    }

    /** 배지에 띄울 안 읽은 개수(NOTI-06). */
    public UnreadCount getUnreadCount(Long userId) {
        return new UnreadCount(notificationRepository.countByReceiver_IdAndReadFalse(userId));
    }

    /**
     * 알림 하나를 읽음으로 바꾼다(NOTI-04). 이미 읽은 알림에 다시 호출해도 {@code markRead()}
     * 자체가 상태 검사 없이 그냥 {@code true}로 바꿔서 구조적으로 멱등하다 — 따로 분기하지 않는다.
     *
     * <p>소유자 확인 하나뿐이라 별도 Guard 클래스 없이 여기서 바로 확인한다 — 이 도메인에서
     * 소유자 확인이 필요한 오퍼레이션이 이거 하나라 클래스를 새로 만들 만큼의 재사용이 없다.
     */
    @Transactional
    public NotificationResponse markNotificationRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        notification.markRead();
        return NotificationResponse.of(notification);
    }

    /** 안 읽은 알림을 한 번에 모두 읽음으로 바꾼다(NOTI-04). 처리 후 개수는 항상 {@code 0}이다. */
    @Transactional
    public UnreadCount markAllNotificationsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
        return new UnreadCount(0);
    }

    /**
     * 알림 종류별 수신 여부를 반환한다(NOTI-03). 사용자가 손댄 적 없는 종류는 행 자체가
     * 없어서(발송 쪽 기본값 로직과 같은 전제 — 행 없으면 켜진 것으로 본다), {@code NotificationType}
     * 전체 값에 실제 설정 행을 병합한다({@code RegionVisitService.listMyRegions()}와 같은 패턴).
     */
    public List<NotificationSettingResponse> getNotificationSettings(Long userId) {
        return toResponses(settingsByType(userId));
    }

    /**
     * 받은 항목만 upsert한다(NOTI-03). 안 보낸 종류는 그대로 둔다.
     *
     * <p>{@code WARNING}을 끄려는 항목은 저장을 시도하기 전에 걸러 {@code INVALID_REQUEST}로
     * 바꿔 던진다 — {@code NotificationSetting.changeEnabled()}가 이 경우 {@code IllegalStateException}을
     * 던지는데, 그대로 두면 처리 안 된 예외로 500이 된다(AUTH의 {@code refreshToken}에서 정확히
     * 같은 패턴으로 실제 겪은 문제라 반드시 먼저 확인한다).
     *
     * <p>새 행 저장은 동시에 같은 (user, type)을 처음 건드리는 두 요청 사이에서 경합할 수 있다
     * (ADR-006). 다만 이건 {@code PostService.like()}의 중복 좋아요와 달리 "같은 사용자가
     * 같은 값을 두 번 보낸 것"에 가까워 클라이언트 에러로 되돌려줄 이유가 없다 — 제약 위반을
     * 잡으면 이미 만들어진 행을 다시 읽어 {@code changeEnabled()}로 원하던 값을 적용한다.
     */
    @Transactional
    public List<NotificationSettingResponse> updateNotificationSettings(
            Long userId, UpdateNotificationSettingsRequest request) {
        Map<NotificationType, NotificationSetting> byType = settingsByType(userId);
        for (UpdateNotificationSettingsRequest.Item item : request.settings()) {
            if (!item.enabled() && !item.type().isMutable()) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            NotificationSetting setting = byType.get(item.type());
            if (setting != null) {
                setting.changeEnabled(item.enabled());
            } else {
                setting = saveNewSettingOrApplyExisting(userId, item.type(), item.enabled());
                byType.put(item.type(), setting);
            }
        }
        return toResponses(byType);
    }

    private NotificationSetting saveNewSettingOrApplyExisting(Long userId, NotificationType type, boolean enabled) {
        try {
            return notificationSettingRepository.saveAndFlush(
                    new NotificationSetting(userRepository.getReferenceById(userId), type, enabled));
        } catch (DataIntegrityViolationException e) {
            NotificationSetting existing = notificationSettingRepository
                    .findById(new NotificationSettingId(userId, type))
                    .orElseThrow(() -> e);
            existing.changeEnabled(enabled);
            return existing;
        }
    }

    private Map<NotificationType, NotificationSetting> settingsByType(Long userId) {
        Map<NotificationType, NotificationSetting> byType = new EnumMap<>(NotificationType.class);
        notificationSettingRepository.findByUser_Id(userId)
                .forEach(setting -> byType.put(setting.getType(), setting));
        return byType;
    }

    private List<NotificationSettingResponse> toResponses(Map<NotificationType, NotificationSetting> byType) {
        return Arrays.stream(NotificationType.values())
                .map(type -> {
                    NotificationSetting setting = byType.get(type);
                    boolean enabled = setting == null || setting.isEnabled();
                    return new NotificationSettingResponse(type, enabled, type.isMutable());
                })
                .toList();
    }
}
