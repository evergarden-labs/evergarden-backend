package com.evergarden.evergardenbackend.user.controller;

import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.user.dto.MyProfile;
import com.evergarden.evergardenbackend.user.dto.ProfileUpdateRequest;
import com.evergarden.evergardenbackend.user.service.MyPageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * MY-01·02. 명세: {@code evergardenapi.yaml}의 {@code /users/me}(GET·PATCH).
 * 같은 경로의 {@code DELETE}(회원 탈퇴, AUTH-04)는 {@code AuthController}가 맡는다 — 메서드가 달라 충돌 없다.
 */
@RestController
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;

    @GetMapping("/users/me")
    public ApiResponse<MyProfile> getMyProfile(@AuthenticationPrincipal AuthPrincipal me) {
        return ApiResponse.of(myPageService.getMyProfile(me.userId()));
    }

    @PatchMapping("/users/me")
    public ApiResponse<MyProfile> updateMyProfile(
            @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.of(myPageService.updateMyProfile(me.userId(), request));
    }
}
