package com.evergarden.evergardenbackend.user.controller;

import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.user.dto.MyProfile;
import com.evergarden.evergardenbackend.user.dto.ProfileUpdateRequest;
import com.evergarden.evergardenbackend.user.service.OnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** ONB-01·02. 명세: {@code evergardenapi.yaml}의 {@code /users/me/onboarding*}. */
@RestController
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PatchMapping("/users/me/onboarding")
    public ApiResponse<MyProfile> completeOnboarding(
            @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.of(onboardingService.completeOnboarding(me.userId(), request));
    }

    @PostMapping("/users/me/onboarding/skip")
    public ApiResponse<MyProfile> skipOnboarding(@AuthenticationPrincipal AuthPrincipal me) {
        return ApiResponse.of(onboardingService.skipOnboarding(me.userId()));
    }
}
