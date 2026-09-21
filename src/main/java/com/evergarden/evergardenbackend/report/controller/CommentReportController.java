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

/** COMM-18. 명세: {@code evergardenapi.yaml}의 {@code /comments/{commentId}/reports}. */
@RestController
@RequestMapping("/comments/{commentId}/reports")
@RequiredArgsConstructor
public class CommentReportController {

    private final ReportService reportService;

    @PostMapping
    public ApiResponse<ReportResult> report(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long commentId,
            @Valid @RequestBody ReportRequest request) {
        return ApiResponse.of(reportService.reportComment(me.userId(), commentId, request));
    }
}
