package com.evergarden.evergardenbackend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.evergarden.evergardenbackend.notification.dto.NotificationResponse;
import com.evergarden.evergardenbackend.notification.entity.Notification;
import com.evergarden.evergardenbackend.notification.entity.NotificationTargetType;
import com.evergarden.evergardenbackend.notification.entity.NotificationType;
import com.evergarden.evergardenbackend.notification.repository.NotificationRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/** 알림 목록·배지(NOTI-01·06)를 다룬다. */
class NotificationQueryServiceTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationQueryService service = new NotificationQueryService(notificationRepository);

    private User receiver() {
        User user = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

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

        long result = service.getUnreadCount(1L);

        assertThat(result).isEqualTo(3L);
    }
}
