package com.evergarden.evergardenbackend.notification.entity;

import com.evergarden.evergardenbackend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 받은 알림(NOTI-01). 앱 내 알림만 있고 푸시는 아직 없다.
 *
 * <p>{@code BaseTimeEntity}를 상속하지 않고 {@code createdAt}만 직접 두지만,
 * {@code @CreatedDate}가 실제로 채워지려면 {@link AuditingEntityListener}가
 * 있어야 한다 — 빠지면 항상 null로 저장을 시도해 {@code NOT NULL} 제약에
 * 매번 걸린다({@code PostLike}에서 실전 확인).
 */
@Entity
@Getter
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 20)
    private NotificationTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Notification(User receiver, NotificationType type, String title, String body,
                         NotificationTargetType targetType, Long targetId) {
        this.receiver = receiver;
        this.type = type;
        this.title = title;
        this.body = body;
        this.targetType = targetType;
        this.targetId = targetId;
        this.read = false;
    }

    /** 읽음 처리(NOTI-04). 이미 읽었어도 오류로 막지 않는다. */
    public void markRead() {
        this.read = true;
    }
}
