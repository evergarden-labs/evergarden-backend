package com.evergarden.evergardenbackend.admin.controller;

import com.evergarden.evergardenbackend.admin.dto.AdminLoginRequest;
import com.evergarden.evergardenbackend.admin.dto.AdminSession;
import com.evergarden.evergardenbackend.admin.service.AdminAuthService;
import com.evergarden.evergardenbackend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN-01·02. 명세: {@code evergardenapi.yaml}의 {@code /admin/auth}. */
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public ApiResponse<AdminSession> login(@Valid @RequestBody AdminLoginRequest request) {
        return ApiResponse.of(adminAuthService.login(request.loginId(), request.password()));
    }

    /** 폐기할 리프레시 토큰이 없어 인증 확인({@code ADMIN_ONLY}) 외에 할 일이 없다. */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.empty();
    }
}
