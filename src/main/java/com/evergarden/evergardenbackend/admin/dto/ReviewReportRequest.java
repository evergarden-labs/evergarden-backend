package com.evergarden.evergardenbackend.admin.dto;

import com.evergarden.evergardenbackend.report.entity.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code reviewReport}(ADMIN-09) 요청 본문. {@code status}는 {@code VALID}·{@code REJECTED}만
 * 허용한다 — {@code PENDING}은 문법적으로는 같은 enum이라 여기서 서비스가 따로 막는다.
 */
public record ReviewReportRequest(@NotNull ReportStatus status, @Size(max = 200) String note) {
}
