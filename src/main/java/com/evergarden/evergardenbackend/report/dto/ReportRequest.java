package com.evergarden.evergardenbackend.report.dto;

import com.evergarden.evergardenbackend.report.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 게시물·댓글·대댓글 신고(COMM-10·18)의 공통 요청 본문.
 *
 * @param detail {@code reason}이 {@link ReportReason#ETC}이면 필수다 — "기타"만으로는
 *               관리자가 판정할 근거가 없다. 이 교차 검증은 필드 하나짜리 애노테이션으로
 *               못 걸어서 서비스에서 확인한다
 */
public record ReportRequest(@NotNull ReportReason reason, @Size(max = 200) String detail) {
}
