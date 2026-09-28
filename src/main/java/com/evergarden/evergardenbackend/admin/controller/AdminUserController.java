package com.evergarden.evergardenbackend.admin.controller;

import com.evergarden.evergardenbackend.admin.dto.AdminUserDetail;
import com.evergarden.evergardenbackend.admin.dto.AdminUserSummary;
import com.evergarden.evergardenbackend.admin.dto.SanctionRequest;
import com.evergarden.evergardenbackend.admin.dto.SanctionResponse;
import com.evergarden.evergardenbackend.admin.service.AdminUserService;
import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.response.PageMeta;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.user.entity.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

/** ADMIN-12·13·04·05·11. 명세: {@code evergardenapi.yaml}의 {@code /admin/users}. */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Validated
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ApiResponse<List<AdminUserSummary>> listAdminUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        Page<AdminUserSummary> result =
                adminUserService.listUsers(keyword, status, PageRequest.of(page - 1, size));
        return ApiResponse.of(result.getContent(), PageMeta.from(result));
    }

    @GetMapping("/{userId}")
    public ApiResponse<AdminUserDetail> getAdminUserDetail(@PathVariable Long userId) {
        return ApiResponse.of(adminUserService.getUserDetail(userId));
    }

    @PostMapping("/{userId}/warnings")
    public ApiResponse<SanctionResponse> warnUser(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long userId,
            @Valid @RequestBody SanctionRequest request) {
        return ApiResponse.of(adminUserService.warnUser(me.userId(), userId, request.reason()));
    }

    @PostMapping("/{userId}/blocks")
    public ApiResponse<SanctionResponse> blockUser(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long userId,
            @Valid @RequestBody SanctionRequest request) {
        return ApiResponse.of(adminUserService.blockUser(me.userId(), userId, request.reason()));
    }

    @DeleteMapping("/{userId}/blocks")
    public ApiResponse<AdminUserDetail> unblockUser(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long userId,
            @Valid @RequestBody SanctionRequest request) {
        return ApiResponse.of(adminUserService.unblockUser(me.userId(), userId, request.reason()));
    }
}
