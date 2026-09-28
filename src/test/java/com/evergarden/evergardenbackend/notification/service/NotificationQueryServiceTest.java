package com.evergarden.evergardenbackend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.notification.dto.NotificationResponse;
import com.evergarden.evergardenbackend.notification.dto.UnreadCount;
import com.evergarden.evergardenbackend.notification.entity.Notification;
import com.evergarden.evergardenbackend.notification.entity.NotificationTargetType;
import com.evergarden.evergardenbackend.notification.entity.NotificationType;
import com.evergarden.evergardenbackend.notification.repository.NotificationRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/** 알림 목록·배지·읽음 처리(NOTI-01·02·04·05·06)를 다룬다. */
class NotificationQueryServiceTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationQueryService service = new NotificationQueryService(notificationRepository);

    private User receiver() {
        User user = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    // ── 목록·배지 ────────────────────────────────────────────

    @Test
    @DisplayName("받은 알림을 최신순으로 반환한다 — type은 구분 없이 다 섞여 나온다(NOTI-02·05)")
    void 목록_조회() {
        User user = receiver();
        Notification invite = Notification.builder()
                .receiver(user).type(NotificationType.COLLAB_INVITE).title("공동 편집 초대")
                .body("아카이브에 초대됐어요").targetType(NotificationTargetType.ARCHIVE).targetId(10L).build();
        ReflectionTestUtils.setField(invite, "id", 1L);
        ReflectionTestUtils.setField(invite, "createdAt", LocalDateTime.now());
        Notification warning = Notification.builder()
                .receiver(user).type(NotificationType.WARNING).title("경고 안내").body("정책 위반").build();
        ReflectionTestUtils.setField(warning, "id", 2L);
        ReflectionTestUtils.setField(warning, "createdAt", LocalDateTime.now());

        PageRequest pageable = PageRequest.of(0, 20);
        Page<Notification> page = new PageImpl<>(List.of(invite, warning), pageable, 2);
        given(notificationRepository.findByReceiver_IdOrderByCreatedAtDesc(1L, pageable)).willReturn(page);

        Page<NotificationResponse> result = service.listNotifications(1L, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).type()).isEqualTo(NotificationType.COLLAB_INVITE);
        assertThat(result.getContent().get(0).targetType()).isEqualTo(NotificationTargetType.ARCHIVE);
        assertThat(result.getContent().get(1).type()).isEqualTo(NotificationType.WARNING);
        assertThat(result.getContent().get(1).targetType()).isNull();
    }

    @Test
    @DisplayName("안 읽은 개수를 그대로 돌려준다")
    void 안읽은개수() {
        given(notificationRepository.countByReceiver_IdAndReadFalse(1L)).willReturn(3L);

        UnreadCount result = service.getUnreadCount(1L);

        assertThat(result.unreadCount()).isEqualTo(3L);
    }

    // ── 읽음 처리 ────────────────────────────────────────────

    @Test
    @DisplayName("내 알림을 읽음으로 바꾼다")
    void 읽음처리_성공() {
        Notification notification = Notification.builder()
                .receiver(receiver()).type(NotificationType.WARNING).title("경고 안내").body("정책 위반").build();
        ReflectionTestUtils.setField(notification, "id", 1L);
        given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));

        NotificationResponse result = service.markNotificationRead(1L, 1L);

        assertThat(result.isRead()).isTrue();
    }

    @Test
    @DisplayName("이미 읽은 알림에 다시 호출해도 200 — 따로 막지 않는다")
    void 읽음처리_이미읽음_멱등() {
        Notification notification = Notification.builder()
                .receiver(receiver()).type(NotificationType.WARNING).title("경고 안내").body("정책 위반").build();
        ReflectionTestUtils.setField(notification, "id", 1L);
        notification.markRead();
        given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));

        NotificationResponse result = service.markNotificationRead(1L, 1L);

        assertThat(result.isRead()).isTrue();
    }

    @Test
    @DisplayName("남의 알림이면 NOT_RESOURCE_OWNER")
    void 읽음처리_소유자아님() {
        User owner = User.builder().nickname("주인").build();
        ReflectionTestUtils.setField(owner, "id", 99L);
        Notification notification = Notification.builder()
                .receiver(owner).type(NotificationType.WARNING).title("경고 안내").body("정책 위반").build();
        ReflectionTestUtils.setField(notification, "id", 1L);
        given(notificationRepository.findById(1L)).willReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.markNotificationRead(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("없는 알림이면 NOTIFICATION_NOT_FOUND")
    void 읽음처리_없는알림() {
        given(notificationRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.markNotificationRead(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("모두 읽음 처리는 벌크 UPDATE를 부르고 항상 0을 돌려준다")
    void 모두읽음처리() {
        UnreadCount result = service.markAllNotificationsRead(1L);

        assertThat(result.unreadCount()).isZero();
        verify(notificationRepository).markAllAsRead(1L);
    }
}
