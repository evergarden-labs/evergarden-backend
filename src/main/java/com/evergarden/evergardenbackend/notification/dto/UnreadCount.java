package com.evergarden.evergardenbackend.notification.dto;

/**
 * {@code getUnreadNotificationCount}(NOTI-06)·{@code markAllNotificationsRead}(NOTI-04) 공통 응답.
 * 후자는 처리 후 항상 {@code 0}이다(명세 {@code enum: [0]}).
 */
public record UnreadCount(long unreadCount) {
}
