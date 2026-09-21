package com.evergarden.evergardenbackend.report.dto;

import com.evergarden.evergardenbackend.report.entity.ReportStatus;

/** 명세의 {@code ReportResult} 스키마. 접수 시점엔 {@code status}가 항상 {@code PENDING}이다. */
public record ReportResult(Long reportId, ReportStatus status) {
}
