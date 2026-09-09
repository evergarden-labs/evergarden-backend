package com.evergarden.evergardenbackend.report.entity;

import com.evergarden.evergardenbackend.global.entity.BaseTimeEntity;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 접수된 신고(COMM-10 · COMM-18).
 *
 * <p>게시물과 댓글 두 종류를 받아 {@code targetType + targetId}로 다형 참조한다.
 * 대신 누적 집계 대상인 {@link #targetUser}는 따로 들고 있어, 콘텐츠가 삭제돼도
 * 카운트가 남는다.
 *
 * <p>같은 대상을 반복 신고할 수 없다. 유니크 제약이 DB에서 막는다(ADR-006).
 */
@Entity
@Getter
@Table(
        name = "reports",
        uniqueConstraints = @UniqueConstraint(
                name = "reports_uk", columnNames = {"reporter_user_id", "target_type", "target_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private ReportTargetType targetType;

    /** 다형 참조라 FK를 걸지 않는다 */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** 신고당한 작성자. 누적 집계의 기준이다 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ReportReason reason;

    /** 덧붙인 설명. {@link ReportReason#ETC}이면 필수 */
    @Column(length = 200)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReportStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private Admin reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 관리자 판정 메모 */
    @Column(length = 200)
    private String note;

    @Builder
    private Report(User reporter, ReportTargetType targetType, Long targetId, User targetUser,
                   ReportReason reason, String detail) {
        this.reporter = reporter;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetUser = targetUser;
        this.reason = reason;
        this.detail = detail;
        this.status = ReportStatus.PENDING;
    }

    /**
     * 유효로 판정한다(ADMIN-09). 이 뒤에 누적 횟수가 오르고, 기준에 닿으면
     * 시스템이 제재를 건다(ADMIN-10).
     */
    public void markValid(Admin admin, LocalDateTime now, String note) {
        review(ReportStatus.VALID, admin, now, note);
    }

    /** 반려한다. 누적 횟수에 반영되지 않는다. */
    public void reject(Admin admin, LocalDateTime now, String note) {
        review(ReportStatus.REJECTED, admin, now, note);
    }

    private void review(ReportStatus status, Admin admin, LocalDateTime now, String note) {
        this.status = status;
        this.reviewedBy = admin;
        this.reviewedAt = now;
        this.note = note;
    }

    public boolean isReviewed() {
        return status != ReportStatus.PENDING;
    }
}
