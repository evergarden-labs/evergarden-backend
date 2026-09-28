package com.evergarden.evergardenbackend.admin.dto;

/** 명세의 {@code AdminDashboard} 스키마(ADMIN-03, ADR-035). 운영 지표와 콘텐츠 지표 두 갈래다. */
public record AdminDashboard(
        int totalUserCount,
        int newUserCountToday,
        int pendingReportCount,
        int sanctionedUserCount,
        int totalArchiveCount,
        int totalTripCount,
        int totalPostCount) {
}
