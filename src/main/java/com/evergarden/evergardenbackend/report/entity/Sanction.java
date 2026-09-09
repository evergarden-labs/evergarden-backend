package com.evergarden.evergardenbackend.report.entity;

import com.evergarden.evergardenbackend.user.entity.Admin;
import com.evergarden.evergardenbackend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

/**
 * 경고·차단·해제 이력.
 *
 * <p>만료 컬럼이 없다. 차단은 무기한이고 관리자가 직접 푼다(ADR-033).
 * 기간제로 바꾸면 만료 시각과 자동 해제 배치가 따라온다.
 */
@Entity
@Getter
@Table(name = "sanctions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sanction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SanctionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SanctionSource source;

    @Column(nullable = false, length = 200)
    private String reason;

    /** 자동 제재면 {@code null} */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by")
    private Admin issuedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Sanction(User user, SanctionType type, SanctionSource source, String reason, Admin issuedBy) {
        this.user = user;
        this.type = type;
        this.source = source;
        this.reason = reason;
        this.issuedBy = issuedBy;
    }

    /** 관리자가 재량으로 건 제재. 사유와 담당자를 남긴다. */
    public static Sanction byAdmin(User user, SanctionType type, String reason, Admin admin) {
        return new Sanction(user, type, SanctionSource.MANUAL, reason, admin);
    }

    /** 유효 신고 누적으로 시스템이 건 제재. 담당자가 없다(ADMIN-10). */
    public static Sanction automatic(User user, SanctionType type, String reason) {
        return new Sanction(user, type, SanctionSource.AUTO, reason, null);
    }
}
