package com.evergarden.evergardenbackend.report.entity;

/** 신고 처리 상태. {@link #VALID}로 판정되어야 누적 횟수에 반영된다(ADMIN-09). */
public enum ReportStatus {
    PENDING,
    VALID,
    REJECTED
}
