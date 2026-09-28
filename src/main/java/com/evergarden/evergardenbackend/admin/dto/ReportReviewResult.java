package com.evergarden.evergardenbackend.admin.dto;

/**
 * 명세의 {@code ReportReviewResult} 스키마(ADMIN-09·10). {@code appliedSanction}은
 * 누적 횟수가 임계값에 닿지 않았거나 반려 판정이면 {@code null}이다.
 */
public record ReportReviewResult(
        AdminReportDetail report, int targetUserValidReportCount, SanctionResponse appliedSanction) {
}
