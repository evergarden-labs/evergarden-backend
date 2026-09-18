package com.evergarden.evergardenbackend.trip.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * {@code POST /posts/{postId}/course/import}(PLAN-12)의 요청 본문.
 *
 * @param startDate 복사본의 시작일. 원본 일수만큼 여기서부터 다시 매긴다(ADR-048).
 *                  생략할 수 없다(ADR-059) — "날짜 없는 초안" 상태를 표현할 데이터
 *                  구조가 없어서다
 * @param title     복사본 제목. 생략하면 원본 제목을 쓴다
 */
public record ImportCourseRequest(@NotNull LocalDate startDate, @Size(max = 60) String title) {
}
