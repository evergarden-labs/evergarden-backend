package com.evergarden.evergardenbackend.admin.controller;

import com.evergarden.evergardenbackend.admin.dto.AdminDashboard;
import com.evergarden.evergardenbackend.admin.service.AdminDashboardService;
import com.evergarden.evergardenbackend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN-03. 명세: {@code evergardenapi.yaml}의 {@code /admin/dashboard}. */
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping
    public ApiResponse<AdminDashboard> getAdminDashboard() {
        return ApiResponse.of(adminDashboardService.getDashboard());
    }
}
