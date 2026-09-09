package com.evergarden.evergardenbackend.notification.entity;

import com.evergarden.evergardenbackend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림 종류별 수신 여부(NOTI-03).
 *
 * <p>시간대 같은 다른 축은 두지 않았다. 지금은 앱 내 알림만 있어
 * "야간 수신 안 함"이 실질적인 효과가 없다(ADR-047).
 */
@Entity
@Getter
@Table(name = "notification_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting {

    @EmbeddedId
    private NotificationSettingId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private boolean enabled;

    public NotificationSetting(User user, NotificationType type, boolean enabled) {
        this.id = new NotificationSettingId(user.getId(), type);
        this.user = user;
        this.enabled = enabled;
    }

    /**
     * 수신 여부를 바꾼다.
     *
     * @throws IllegalStateException 경고 알림을 끄려 할 때. 운영 통지라 수신 거부 대상이 아니다
     */
    public void changeEnabled(boolean enabled) {
        if (!enabled && !id.getType().isMutable()) {
            throw new IllegalStateException("경고 알림은 끌 수 없습니다: " + id.getType());
        }
        this.enabled = enabled;
    }

    public NotificationType getType() {
        return id.getType();
    }
}
