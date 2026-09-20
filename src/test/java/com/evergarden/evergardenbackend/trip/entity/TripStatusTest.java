package com.evergarden.evergardenbackend.trip.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 오늘 날짜와 여행 기간을 비교해 상태를 계산하는 경계값을 확인한다(ADR-040). */
class TripStatusTest {

    private static final LocalDate START = LocalDate.of(2026, 3, 5);
    private static final LocalDate END = LocalDate.of(2026, 3, 8);

    @Test
    @DisplayName("오늘이 시작일 하루 전이면 UPCOMING")
    void 시작일_하루전() {
        assertThat(TripStatus.of(START, END, START.minusDays(1))).isEqualTo(TripStatus.UPCOMING);
    }

    @Test
    @DisplayName("오늘이 시작일 당일이면 ONGOING — 경계는 포함")
    void 시작일_당일() {
        assertThat(TripStatus.of(START, END, START)).isEqualTo(TripStatus.ONGOING);
    }

    @Test
    @DisplayName("오늘이 여행 기간 중이면 ONGOING")
    void 기간중() {
        assertThat(TripStatus.of(START, END, START.plusDays(1))).isEqualTo(TripStatus.ONGOING);
    }

    @Test
    @DisplayName("오늘이 종료일 당일이면 ONGOING — 경계는 포함")
    void 종료일_당일() {
        assertThat(TripStatus.of(START, END, END)).isEqualTo(TripStatus.ONGOING);
    }

    @Test
    @DisplayName("오늘이 종료일 하루 후면 PAST")
    void 종료일_하루후() {
        assertThat(TripStatus.of(START, END, END.plusDays(1))).isEqualTo(TripStatus.PAST);
    }

    @Test
    @DisplayName("당일치기 여행(시작일=종료일)도 그 날은 ONGOING")
    void 당일치기() {
        assertThat(TripStatus.of(START, START, START)).isEqualTo(TripStatus.ONGOING);
    }
}
