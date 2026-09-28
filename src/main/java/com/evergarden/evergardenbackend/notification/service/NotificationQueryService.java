package com.evergarden.evergardenbackend.notification.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.notification.dto.NotificationResponse;
import com.evergarden.evergardenbackend.notification.dto.UnreadCount;
import com.evergarden.evergardenbackend.notification.entity.Notification;
import com.evergarden.evergardenbackend.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
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
}
