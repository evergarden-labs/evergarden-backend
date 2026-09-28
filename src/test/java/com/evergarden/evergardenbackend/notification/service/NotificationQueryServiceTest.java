package com.evergarden.evergardenbackend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.notification.dto.NotificationResponse;
import com.evergarden.evergardenbackend.notification.dto.NotificationSettingResponse;
import com.evergarden.evergardenbackend.notification.dto.UnreadCount;
import com.evergarden.evergardenbackend.notification.dto.UpdateNotificationSettingsRequest;
import com.evergarden.evergardenbackend.notification.entity.Notification;
import com.evergarden.evergardenbackend.notification.entity.NotificationSetting;
import com.evergarden.evergardenbackend.notification.entity.NotificationSettingId;
import com.evergarden.evergardenbackend.notification.entity.NotificationTargetType;
import com.evergarden.evergardenbackend.notification.entity.NotificationType;
import com.evergarden.evergardenbackend.notification.repository.NotificationRepository;
import com.evergarden.evergardenbackend.notification.repository.NotificationSettingRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/** 알림 목록·배지·읽음 처리·설정(NOTI-01~06)을 다룬다. */
class NotificationQueryServiceTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationSettingRepository notificationSettingRepository = mock(NotificationSettingRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NotificationQueryService service =
            new NotificationQueryService(notificationRepository, notificationSettingRepository, userRepository);

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

    // ── 알림 설정 ────────────────────────────────────────────

    @Test
    @DisplayName("손댄 적 없으면 전체 종류가 다 켜진 상태로 나온다 — WARNING만 editable=false")
    void 설정_조회_기본값() {
        given(notificationSettingRepository.findByUser_Id(1L)).willReturn(List.of());

        List<NotificationSettingResponse> result = service.getNotificationSettings(1L);

        assertThat(result).hasSize(NotificationType.values().length);
        assertThat(result).allMatch(NotificationSettingResponse::enabled);
        assertThat(result).filteredOn(r -> r.type() == NotificationType.WARNING)
                .extracting(NotificationSettingResponse::editable).containsExactly(false);
        assertThat(result).filteredOn(r -> r.type() != NotificationType.WARNING)
                .extracting(NotificationSettingResponse::editable).containsOnly(true);
    }

    @Test
    @DisplayName("행이 있는 종류는 그 값을, 없는 종류는 기본값을 섞어서 돌려준다")
    void 설정_조회_병합() {
        User user = receiver();
        NotificationSetting inviteOff = new NotificationSetting(user, NotificationType.COLLAB_INVITE, false);
        given(notificationSettingRepository.findByUser_Id(1L)).willReturn(List.of(inviteOff));

        List<NotificationSettingResponse> result = service.getNotificationSettings(1L);

        assertThat(result).filteredOn(r -> r.type() == NotificationType.COLLAB_INVITE)
                .extracting(NotificationSettingResponse::enabled).containsExactly(false);
        assertThat(result).filteredOn(r -> r.type() == NotificationType.CAPSULE_UNLOCK)
                .extracting(NotificationSettingResponse::enabled).containsExactly(true);
    }

    @Test
    @DisplayName("기존 행이 있으면 changeEnabled로 바꾼다")
    void 설정_변경_기존행() {
        User user = receiver();
        NotificationSetting invite = new NotificationSetting(user, NotificationType.COLLAB_INVITE, true);
        given(notificationSettingRepository.findByUser_Id(1L)).willReturn(List.of(invite));
        UpdateNotificationSettingsRequest request =
                new UpdateNotificationSettingsRequest(List.of(new UpdateNotificationSettingsRequest.Item(
                        NotificationType.COLLAB_INVITE, false)));

        List<NotificationSettingResponse> result = service.updateNotificationSettings(1L, request);

        assertThat(invite.isEnabled()).isFalse();
        assertThat(result).filteredOn(r -> r.type() == NotificationType.COLLAB_INVITE)
                .extracting(NotificationSettingResponse::enabled).containsExactly(false);
        verify(notificationSettingRepository, never()).save(any());
    }

    @Test
    @DisplayName("행이 없던 종류를 새로 켜거나 끄면 새로 만든다")
    void 설정_변경_신규행() {
        given(notificationSettingRepository.findByUser_Id(1L)).willReturn(List.of());
        given(userRepository.getReferenceById(1L)).willReturn(receiver());
        UpdateNotificationSettingsRequest request =
                new UpdateNotificationSettingsRequest(List.of(new UpdateNotificationSettingsRequest.Item(
                        NotificationType.CAPSULE_UNLOCK, false)));

        service.updateNotificationSettings(1L, request);

        verify(notificationSettingRepository).saveAndFlush(argThat(
                setting -> setting.getType() == NotificationType.CAPSULE_UNLOCK && !setting.isEnabled()));
    }

    @Test
    @DisplayName("신규 행 저장이 경합으로 제약 위반이 나면, 이미 만들어진 행을 읽어 원하던 값을 적용한다")
    void 설정_변경_신규행_경합() {
        User user = receiver();
        NotificationSetting racedIn = new NotificationSetting(user, NotificationType.CAPSULE_UNLOCK, true);
        given(notificationSettingRepository.findByUser_Id(1L)).willReturn(List.of());
        given(userRepository.getReferenceById(1L)).willReturn(user);
        given(notificationSettingRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("duplicate"));
        given(notificationSettingRepository.findById(new NotificationSettingId(1L, NotificationType.CAPSULE_UNLOCK)))
                .willReturn(Optional.of(racedIn));
        UpdateNotificationSettingsRequest request =
                new UpdateNotificationSettingsRequest(List.of(new UpdateNotificationSettingsRequest.Item(
                        NotificationType.CAPSULE_UNLOCK, false)));

        List<NotificationSettingResponse> result = service.updateNotificationSettings(1L, request);

        assertThat(racedIn.isEnabled()).isFalse();
        assertThat(result).filteredOn(r -> r.type() == NotificationType.CAPSULE_UNLOCK)
                .extracting(NotificationSettingResponse::enabled).containsExactly(false);
    }

    @Test
    @DisplayName("WARNING을 끄려 하면 INVALID_REQUEST — 엔티티 예외에 기대지 않는다")
    void 설정_변경_경고끄기_거절() {
        given(notificationSettingRepository.findByUser_Id(1L)).willReturn(List.of());
        UpdateNotificationSettingsRequest request =
                new UpdateNotificationSettingsRequest(List.of(new UpdateNotificationSettingsRequest.Item(
                        NotificationType.WARNING, false)));

        assertThatThrownBy(() -> service.updateNotificationSettings(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }
}
