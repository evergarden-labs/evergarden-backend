package com.evergarden.evergardenbackend.auth.controller;

import com.evergarden.evergardenbackend.auth.dto.AuthResult;
import com.evergarden.evergardenbackend.auth.dto.RefreshTokenRequest;
import com.evergarden.evergardenbackend.auth.dto.SocialLoginRequest;
import com.evergarden.evergardenbackend.auth.dto.TokenPair;
import com.evergarden.evergardenbackend.auth.service.AuthService;
import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** AUTH-01~05·07. 명세: {@code evergardenapi.yaml}의 인증 오퍼레이션들. */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/auth/social/{provider}")
    public ApiResponse<AuthResult> loginWithSocial(
            @PathVariable String provider,
            @Valid @RequestBody SocialLoginRequest request) {
        return ApiResponse.of(authService.loginWithSocial(provider, request.socialAccessToken()));
    }

    @PostMapping("/auth/restore/{provider}")
    public ApiResponse<AuthResult> restoreAccount(
            @PathVariable String provider,
            @Valid @RequestBody SocialLoginRequest request) {
        return ApiResponse.of(authService.restoreAccount(provider, request.socialAccessToken()));
    }

    @PostMapping("/auth/token/refresh")
    public ApiResponse<TokenPair> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.of(authService.refreshToken(request.refreshToken()));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthPrincipal me) {
        authService.logout(me.userId());
        return ApiResponse.empty();
    }

    @DeleteMapping("/users/me")
    public ApiResponse<Void> withdrawMe(@AuthenticationPrincipal AuthPrincipal me) {
        authService.withdraw(me.userId());
        return ApiResponse.empty();
    }
}
