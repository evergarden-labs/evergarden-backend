package com.evergarden.evergardenbackend.trip.entity;

import java.time.LocalDate;

/** 일정 상태. DB 컬럼이 아니라 오늘 날짜와 기간을 비교해 매번 계산한다(ADR-040). */
public enum TripStatus {
    UPCOMING,
    ONGOING,
    PAST;

    public static TripStatus of(LocalDate startDate, LocalDate endDate, LocalDate today) {
        if (today.isBefore(startDate)) {
            return UPCOMING;
        }
        if (today.isAfter(endDate)) {
            return PAST;
        }
        return ONGOING;
    }
}
