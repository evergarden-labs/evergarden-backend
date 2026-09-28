package com.evergarden.evergardenbackend.admin.controller;

import com.evergarden.evergardenbackend.admin.dto.AdminReportDetail;
import com.evergarden.evergardenbackend.admin.dto.AdminReportSummary;
import com.evergarden.evergardenbackend.admin.dto.ReportReviewResult;
import com.evergarden.evergardenbackend.admin.dto.ReviewReportRequest;
import com.evergarden.evergardenbackend.admin.service.AdminReportService;
import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.response.PageMeta;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.report.entity.ReportStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN-14·15·09·10. 명세: {@code evergardenapi.yaml}의 {@code /admin/reports}. */
@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
@Validated
public class AdminReportController {

    private final AdminReportService adminReportService;

    @GetMapping
    public ApiResponse<List<AdminReportSummary>> listAdminReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        Page<AdminReportSummary> result =
                adminReportService.listReports(status, PageRequest.of(page - 1, size));
        return ApiResponse.of(result.getContent(), PageMeta.from(result));
    }

    @GetMapping("/{reportId}")
    public ApiResponse<AdminReportDetail> getAdminReportDetail(@PathVariable Long reportId) {
        return ApiResponse.of(adminReportService.getReportDetail(reportId));
    }

    @PatchMapping("/{reportId}")
    public ApiResponse<ReportReviewResult> reviewReport(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long reportId,
            @Valid @RequestBody ReviewReportRequest request) {
        return ApiResponse.of(
                adminReportService.reviewReport(me.userId(), reportId, request.status(), request.note()));
    }
}
