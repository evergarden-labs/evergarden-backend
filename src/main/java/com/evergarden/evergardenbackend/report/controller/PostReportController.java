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

/** COMM-10. 명세: {@code evergardenapi.yaml}의 {@code /posts/{postId}/reports}. */
@RestController
@RequestMapping("/posts/{postId}/reports")
@RequiredArgsConstructor
public class PostReportController {

    private final ReportService reportService;

    @PostMapping
    public ApiResponse<ReportResult> report(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long postId,
            @Valid @RequestBody ReportRequest request) {
        return ApiResponse.of(reportService.reportPost(me.userId(), postId, request));
    }
}
