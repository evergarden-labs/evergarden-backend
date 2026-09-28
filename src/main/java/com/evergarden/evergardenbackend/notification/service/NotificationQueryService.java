package com.evergarden.evergardenbackend.notification.service;

import com.evergarden.evergardenbackend.notification.dto.NotificationResponse;
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
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByReceiver_IdAndReadFalse(userId);
    }
}
