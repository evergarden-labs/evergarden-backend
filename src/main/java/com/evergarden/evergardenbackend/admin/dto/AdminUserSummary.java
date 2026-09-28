package com.evergarden.evergardenbackend.admin.dto;

import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.entity.UserStatus;
import java.time.LocalDateTime;

/** 명세의 {@code AdminUserSummary} 스키마(ADMIN-12). */
public record AdminUserSummary(
        Long userId, String nickname, UserStatus status, int validReportCount, LocalDateTime createdAt) {

    public static AdminUserSummary of(User user) {
        return new AdminUserSummary(
                user.getId(), user.getNickname(), user.getStatus(), user.getValidReportCount(), user.getCreatedAt());
    }
}
