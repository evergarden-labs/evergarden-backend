package com.evergarden.evergardenbackend.trip.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * {@code POST /posts/{postId}/course/import}(PLAN-12)의 요청 본문.
 *
 * @param startDate 복사본의 시작일. 원본 일수만큼 여기서부터 다시 매긴다(ADR-048).
 *                  명세는 생략을 허용하지만("기간 없는 초안"), 그 상태를 표현할 데이터
 *                  구조가 아직 없어(docs/decisions.md 참고) 지금은 사실상 필수로 다룬다 —
 *                  생략하면 {@code INVALID_REQUEST}
 * @param title     복사본 제목. 생략하면 원본 제목을 쓴다
 */
public record ImportCourseRequest(LocalDate startDate, @Size(max = 60) String title) {
}
