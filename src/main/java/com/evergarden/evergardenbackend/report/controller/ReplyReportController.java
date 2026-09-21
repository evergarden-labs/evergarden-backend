package com.evergarden.evergardenbackend.report.controller;

import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.report.dto.ReportRequest;
import com.evergarden.evergardenbackend.report.dto.ReportResult;
import com.evergarden.evergardenbackend.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * COMM-18. 명세: {@code evergardenapi.yaml}의 {@code /replies/{replyId}/reports}.
 * 저장은 {@code reportComment}와 완전히 같은 테이블·로직이다 — 경로만 나뉘어 있다.
 */
@RestController
@RequestMapping("/replies/{replyId}/reports")
@RequiredArgsConstructor
public class ReplyReportController {

    private final ReportService reportService;

    @PostMapping
    public ApiResponse<ReportResult> report(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long replyId,
            @Valid @RequestBody ReportRequest request) {
        return ApiResponse.of(reportService.reportReply(me.userId(), replyId, request));
    }
}
