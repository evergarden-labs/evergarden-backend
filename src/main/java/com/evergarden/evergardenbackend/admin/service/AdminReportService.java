package com.evergarden.evergardenbackend.admin.service;

import com.evergarden.evergardenbackend.admin.dto.AdminReportDetail;
import com.evergarden.evergardenbackend.admin.dto.AdminReportSummary;
import com.evergarden.evergardenbackend.admin.dto.ReportReviewResult;
import com.evergarden.evergardenbackend.admin.dto.SanctionResponse;
import com.evergarden.evergardenbackend.community.entity.Comment;
import com.evergarden.evergardenbackend.community.entity.CommentStatus;
import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.PostStatus;
import com.evergarden.evergardenbackend.community.repository.CommentRepository;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.security.RefreshTokenStore;
import com.evergarden.evergardenbackend.notification.entity.NotificationType;
import com.evergarden.evergardenbackend.notification.service.NotificationService;
import com.evergarden.evergardenbackend.report.entity.Report;
import com.evergarden.evergardenbackend.report.entity.ReportStatus;
import com.evergarden.evergardenbackend.report.entity.ReportTargetType;
import com.evergarden.evergardenbackend.report.entity.Sanction;
import com.evergarden.evergardenbackend.report.entity.SanctionType;
import com.evergarden.evergardenbackend.report.repository.ReportRepository;
import com.evergarden.evergardenbackend.report.repository.SanctionRepository;
import com.evergarden.evergardenbackend.user.entity.Admin;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.entity.UserStatus;
import com.evergarden.evergardenbackend.user.repository.AdminRepository;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 관리·판정·자동 제재(ADMIN-14·15·09·10).
 *
 * <p>{@code appliedSanction}은 유효 신고 누적이 1회면 경고, 3회면 차단을 자동으로 건다
 * (관리자가 따로 누르지 않는다). 4회 이상 계속 쌓여도 임계값을 넘는 순간이 아니면
 * {@code appliedSanction}은 계속 {@code null}이다 — 이미 그 이상 수준으로 제재된 상태라
 * 매번 새 제재를 반복해서 걸 이유가 없어서다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportService {

    private static final int WARNING_THRESHOLD = 1;
    private static final int BLOCK_THRESHOLD = 3;

    private final ReportRepository reportRepository;
    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final SanctionRepository sanctionRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final NotificationService notificationService;

    public Page<AdminReportSummary> listReports(ReportStatus status, Pageable pageable) {
        return reportRepository.search(status, pageable).map(AdminReportSummary::of);
    }

    public AdminReportDetail getReportDetail(Long reportId) {
        return buildDetail(findReport(reportId));
    }

    /**
     * 신고를 유효 또는 반려로 판정한다(ADMIN-09). 유효 판정이면 대상 회원의 누적 유효
     * 신고 횟수를 올리고, 임계값에 닿으면 시스템이 자동으로 제재를 건다(ADMIN-10).
     *
     * <p>누적 횟수는 {@code targetUser.increaseValidReportCount()}(엔티티 메모리 증가 후
     * flush)가 아니라 {@code userRepository.increaseValidReportCount()}(원자적 {@code UPDATE})로
     * 늘린다 — 같은 회원을 겨냥한 서로 다른 신고 3건을 동시에 유효 판정하는 걸 실제로
     * 재현해보니, 엔티티 방식은 최종값이 3이 아니라 1로 끝났다(손실 업데이트).
     */
    @Transactional
    public ReportReviewResult reviewReport(Long adminId, Long reportId, ReportStatus decision, String note) {
        if (decision != ReportStatus.VALID && decision != ReportStatus.REJECTED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Report report = findReport(reportId);
        if (report.isReviewed()) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_REVIEWED);
        }

        Admin admin = adminRepository.getReferenceById(adminId);
        LocalDateTime now = LocalDateTime.now();
        User targetUser = report.getTargetUser();
        int validReportCount = targetUser.getValidReportCount();
        SanctionResponse appliedSanction = null;
        if (decision == ReportStatus.VALID) {
            report.markValid(admin, now, note);
            userRepository.increaseValidReportCount(targetUser.getId());
            validReportCount = userRepository.findValidReportCount(targetUser.getId());
            appliedSanction = applyAutomaticSanctionIfThreshold(targetUser, validReportCount);
        } else {
            report.reject(admin, now, note);
        }

        return new ReportReviewResult(buildDetail(report), validReportCount, appliedSanction);
    }

    /**
     * 임계값(1회 경고, 3회 차단)에 정확히 닿았을 때만 제재를 건다. 그 밖엔 {@code null}.
     *
     * <p>상태 변경도 {@code user.warn()/.block()} 대신 {@code userRepository.updateStatus()}
     * (원자적 {@code UPDATE})로 한다 — 엔티티를 메모리에서 바꾸고 더티 체킹으로 flush하면
     * {@code @DynamicUpdate}가 없는 이 엔티티는 매핑된 모든 컬럼을 다시 쓰기 때문에,
     * 방금 {@link #reviewReport}가 원자적으로 늘려둔 {@code validReportCount}를
     * 이 엔티티가 로드됐을 때의 낡은 값으로 덮어써 버릴 수 있다.
     */
    private SanctionResponse applyAutomaticSanctionIfThreshold(User user, int validReportCount) {
        if (validReportCount == WARNING_THRESHOLD) {
            userRepository.updateStatus(user.getId(), UserStatus.WARNED);
            Sanction sanction = sanctionRepository.save(
                    Sanction.automatic(user, SanctionType.WARNING, "유효 신고 누적 " + validReportCount + "회"));
            notificationService.notify(user, NotificationType.WARNING, "경고 안내",
                    "누적된 신고로 경고 처리됐습니다.", null, null);
            return SanctionResponse.of(sanction);
        }
        if (validReportCount == BLOCK_THRESHOLD) {
            userRepository.updateStatus(user.getId(), UserStatus.BLOCKED);
            refreshTokenStore.revoke(user.getId());
            Sanction sanction = sanctionRepository.save(
                    Sanction.automatic(user, SanctionType.BLOCK, "유효 신고 누적 " + validReportCount + "회"));
            return SanctionResponse.of(sanction);
        }
        return null;
    }

    private AdminReportDetail buildDetail(Report report) {
        TargetContent target = resolveTarget(report.getTargetType(), report.getTargetId());
        return AdminReportDetail.of(report, target.content(), target.deleted());
    }

    /**
     * 신고 대상의 실제 내용을 읽는다. 이미 삭제됐으면(ADR-007, 상태만 바뀌고 행은 남음)
     * 내용은 {@code null}로 감추고 {@code targetDeleted}만 {@code true}로 알린다.
     */
    private TargetContent resolveTarget(ReportTargetType targetType, Long targetId) {
        if (targetType == ReportTargetType.POST) {
            Post post = postRepository.findById(targetId).orElse(null);
            if (post == null || post.getStatus() == PostStatus.DELETED) {
                return new TargetContent(null, true);
            }
            return new TargetContent(post.getContent(), false);
        }
        Comment comment = commentRepository.findById(targetId).orElse(null);
        if (comment == null || comment.getStatus() == CommentStatus.DELETED) {
            return new TargetContent(null, true);
        }
        return new TargetContent(comment.getContent(), false);
    }

    private record TargetContent(String content, boolean deleted) {
    }

    private Report findReport(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
    }
}
