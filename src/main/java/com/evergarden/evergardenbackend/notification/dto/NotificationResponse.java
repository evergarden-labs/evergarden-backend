package com.evergarden.evergardenbackend.notification.dto;

import com.evergarden.evergardenbackend.notification.entity.Notification;
import com.evergarden.evergardenbackend.notification.entity.NotificationTargetType;
import com.evergarden.evergardenbackend.notification.entity.NotificationType;
import java.time.LocalDateTime;

/** 명세의 {@code Notification} 스키마(NOTI-01). */
public record NotificationResponse(
        Long notificationId,
        NotificationType type,
        String title,
        String body,
        NotificationTargetType targetType,
        Long targetId,
        boolean isRead,
        LocalDateTime createdAt) {

    public static NotificationResponse of(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
