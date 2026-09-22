package com.evergarden.evergardenbackend.notification.service;

import com.evergarden.evergardenbackend.notification.entity.Notification;
import com.evergarden.evergardenbackend.notification.entity.NotificationSetting;
import com.evergarden.evergardenbackend.notification.entity.NotificationSettingId;
import com.evergarden.evergardenbackend.notification.entity.NotificationTargetType;
import com.evergarden.evergardenbackend.notification.entity.NotificationType;
import com.evergarden.evergardenbackend.notification.repository.NotificationRepository;
import com.evergarden.evergardenbackend.notification.repository.NotificationSettingRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 발송(NOTI-01 이하). 지금은 앱 내 알림 하나뿐이라 이 메서드 하나로 충분하다 —
 * 타임캡슐 위치 해제(TC-04), 공동 편집 초대(NOTI-02) 등 여러 도메인이 같이 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;

    /**
     * {@code NotificationSetting} 행이 없으면 켜진 것으로 본다 — 사용자가 명시적으로
     * 끈 적이 없다는 뜻이라서다(NOTI-03). {@code WARNING}처럼 끌 수 없는 종류는
     * 설정 행 자체가 만들어지지 않으니 이 기본값이 항상 적용된다.
     */
    public void notify(User receiver, NotificationType type, String title, String body,
                        NotificationTargetType targetType, Long targetId) {
        boolean enabled = notificationSettingRepository
                .findById(new NotificationSettingId(receiver.getId(), type))
                .map(NotificationSetting::isEnabled)
                .orElse(true);
        if (!enabled) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .receiver(receiver)
                .type(type)
                .title(title)
                .body(body)
                .targetType(targetType)
                .targetId(targetId)
                .build());
    }
}
