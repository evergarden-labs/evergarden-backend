package com.evergarden.evergardenbackend.notification.dto;

import com.evergarden.evergardenbackend.notification.entity.NotificationType;

/**
 * 명세의 {@code NotificationSetting} 스키마(NOTI-03).
 *
 * @param editable {@code false}면 사용자가 끌 수 없다 — {@code WARNING}이 여기 해당한다
 */
public record NotificationSettingResponse(NotificationType type, boolean enabled, boolean editable) {
}
