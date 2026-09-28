package com.evergarden.evergardenbackend.admin.dto;

import com.evergarden.evergardenbackend.community.dto.Author;
import com.evergarden.evergardenbackend.report.entity.Report;
import com.evergarden.evergardenbackend.report.entity.ReportReason;
import com.evergarden.evergardenbackend.report.entity.ReportStatus;
import com.evergarden.evergardenbackend.report.entity.ReportTargetType;
import java.time.LocalDateTime;

/** 명세의 {@code AdminReportSummary} 스키마(ADMIN-14). */
public record AdminReportSummary(
        Long reportId,
        ReportTargetType targetType,
        Long targetId,
        Author targetAuthor,
        ReportReason reason,
        String detail,
        ReportStatus status,
        Author reporter,
        LocalDateTime createdAt) {

    public static AdminReportSummary of(Report report) {
        return new AdminReportSummary(
                report.getId(), report.getTargetType(), report.getTargetId(),
                Author.of(report.getTargetUser()), report.getReason(), report.getDetail(), report.getStatus(),
                Author.of(report.getReporter()), report.getCreatedAt());
    }
}
