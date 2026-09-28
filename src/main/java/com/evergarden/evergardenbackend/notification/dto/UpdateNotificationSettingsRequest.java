package com.evergarden.evergardenbackend.notification.dto;

import com.evergarden.evergardenbackend.notification.entity.NotificationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** {@code PATCH /notifications/settings}(NOTI-03)의 요청 본문. 보내지 않은 종류는 그대로 둔다. */
public record UpdateNotificationSettingsRequest(@NotEmpty @Valid List<Item> settings) {

    public record Item(@NotNull NotificationType type, @NotNull Boolean enabled) {
    }
}
