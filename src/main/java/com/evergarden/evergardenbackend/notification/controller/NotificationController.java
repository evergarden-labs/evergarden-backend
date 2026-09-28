package com.evergarden.evergardenbackend.notification.controller;

import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.response.PageMeta;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.notification.dto.NotificationResponse;
import com.evergarden.evergardenbackend.notification.dto.NotificationSettingResponse;
import com.evergarden.evergardenbackend.notification.dto.UnreadCount;
import com.evergarden.evergardenbackend.notification.dto.UpdateNotificationSettingsRequest;
import com.evergarden.evergardenbackend.notification.service.NotificationQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** NOTI-01~06. 명세: {@code evergardenapi.yaml}의 {@code /notifications}. */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    @GetMapping
    public ApiResponse<List<NotificationResponse>> listNotifications(
            @AuthenticationPrincipal AuthPrincipal me,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        Page<NotificationResponse> result =
                notificationQueryService.listNotifications(me.userId(), PageRequest.of(page - 1, size));
        return ApiResponse.of(result.getContent(), PageMeta.from(result));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCount> getUnreadNotificationCount(@AuthenticationPrincipal AuthPrincipal me) {
        return ApiResponse.of(notificationQueryService.getUnreadCount(me.userId()));
    }

    @PostMapping("/read-all")
    public ApiResponse<UnreadCount> markAllNotificationsRead(@AuthenticationPrincipal AuthPrincipal me) {
        return ApiResponse.of(notificationQueryService.markAllNotificationsRead(me.userId()));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponse> markNotificationRead(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long notificationId) {
        return ApiResponse.of(notificationQueryService.markNotificationRead(me.userId(), notificationId));
    }

    @GetMapping("/settings")
    public ApiResponse<List<NotificationSettingResponse>> getNotificationSettings(
            @AuthenticationPrincipal AuthPrincipal me) {
        return ApiResponse.of(notificationQueryService.getNotificationSettings(me.userId()));
    }

    @PatchMapping("/settings")
    public ApiResponse<List<NotificationSettingResponse>> updateNotificationSettings(
            @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody UpdateNotificationSettingsRequest request) {
        return ApiResponse.of(notificationQueryService.updateNotificationSettings(me.userId(), request));
    }
}
