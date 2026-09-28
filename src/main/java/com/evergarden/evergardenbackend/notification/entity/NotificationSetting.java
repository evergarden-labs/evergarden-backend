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
import org.springframework.data.domain.Persistable;

/**
 * 알림 종류별 수신 여부(NOTI-03).
 *
 * <p>시간대 같은 다른 축은 두지 않았다. 지금은 앱 내 알림만 있어
 * "야간 수신 안 함"이 실질적인 효과가 없다(ADR-047).
 *
 * <p>{@code @EmbeddedId}는 생성자에서 이미 값이 채워지므로, {@link Persistable}을
 * 구현하지 않으면 Spring Data가 "이미 있는 행"으로 보고 {@code merge()}를 써서
 * 중복 저장을 조용히 UPDATE로 처리해 버린다({@code PostLike}에서 실전에 확인된
 * 것과 같은 문제 — {@code NotificationQueryService.updateNotificationSettings()}의
 * 신규 행 생성 경로에서 똑같이 재현됨). {@link #isNew()}가 항상 {@code true}인
 * 이유도 {@code PostLike}와 같다 — 기존 행 수정은 {@code changeEnabled()}로
 * 관리 중인 엔티티를 바꾸기만 할 뿐 다시 {@code save()}를 부르지 않으므로,
 * {@code isNew()} 고정이 영향을 주는 건 신규 행 저장 경로뿐이다.
 */
@Entity
@Getter
@Table(name = "notification_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting implements Persistable<NotificationSettingId> {

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

    @Override
    public NotificationSettingId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return true;
    }
}
