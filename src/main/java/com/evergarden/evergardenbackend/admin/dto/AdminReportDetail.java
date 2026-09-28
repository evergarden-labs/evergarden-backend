package com.evergarden.evergardenbackend.admin.dto;

import com.evergarden.evergardenbackend.community.dto.Author;
import com.evergarden.evergardenbackend.report.entity.Report;
import com.evergarden.evergardenbackend.report.entity.ReportReason;
import com.evergarden.evergardenbackend.report.entity.ReportStatus;
import com.evergarden.evergardenbackend.report.entity.ReportTargetType;
import java.time.LocalDateTime;

/**
 * 명세의 {@code AdminReportDetail} 스키마(ADMIN-15) — {@code AdminReportSummary}에
 * 실제 콘텐츠·판정 정보를 더한 모양이다({@code ArchiveDetail}과 같은 방식으로 필드를 펼침).
 */
public record AdminReportDetail(
        Long reportId,
        ReportTargetType targetType,
        Long targetId,
        Author targetAuthor,
        ReportReason reason,
        String detail,
        ReportStatus status,
        Author reporter,
        LocalDateTime createdAt,
        String targetContent,
        boolean targetDeleted,
        String reviewedByAdminName,
        LocalDateTime reviewedAt,
        String note) {

    public static AdminReportDetail of(Report report, String targetContent, boolean targetDeleted) {
        return new AdminReportDetail(
                report.getId(), report.getTargetType(), report.getTargetId(),
                Author.of(report.getTargetUser()), report.getReason(), report.getDetail(), report.getStatus(),
                Author.of(report.getReporter()), report.getCreatedAt(),
                targetContent, targetDeleted,
                report.getReviewedBy() == null ? null : report.getReviewedBy().getName(),
                report.getReviewedAt(), report.getNote());
    }
}
