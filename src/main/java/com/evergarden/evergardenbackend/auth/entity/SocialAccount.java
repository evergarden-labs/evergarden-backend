package com.evergarden.evergardenbackend.auth.entity;

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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 회원에 연결된 소셜 계정.
 *
 * <p>{@code (provider, providerUserId)}가 유일해서 같은 소셜 계정으로 두 번 가입할 수 없다.
 * 탈퇴 유예 중에는 이 행이 남아 있어 재가입이 아니라 복구만 된다(ADR-054).
 *
 * <p>{@code BaseTimeEntity}를 상속하지 않고 {@code createdAt}만 직접 두지만,
 * {@code @CreatedDate}가 실제로 채워지려면 {@link AuditingEntityListener}가
 * 있어야 한다 — 빠지면 항상 null로 저장을 시도해 {@code NOT NULL} 제약에
 * 매번 걸린다({@code PostLike}에서 실전 확인).
 */
@Entity
@Getter
@Table(
        name = "social_accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "social_accounts_uk",
                columnNames = {"provider", "provider_user_id"}))
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SocialProvider provider;

    /** 제공자가 주는 고유 식별자 */
    @Column(name = "provider_user_id", nullable = false, length = 100)
    private String providerUserId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private SocialAccount(User user, SocialProvider provider, String providerUserId) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }
}
