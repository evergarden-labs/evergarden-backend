package com.evergarden.evergardenbackend.timecapsule.controller;

import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.timecapsule.dto.TimeCapsuleCreateRequest;
import com.evergarden.evergardenbackend.timecapsule.dto.TimeCapsuleDetail;
import com.evergarden.evergardenbackend.timecapsule.service.TimeCapsuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** TC-01 이하. 명세: {@code evergardenapi.yaml}의 {@code /time-capsules}. */
@RestController
@RequestMapping("/time-capsules")
@RequiredArgsConstructor
public class TimeCapsuleController {

    private final TimeCapsuleService timeCapsuleService;

    @PostMapping
    public ApiResponse<TimeCapsuleDetail> create(
            @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody TimeCapsuleCreateRequest request) {
        return ApiResponse.of(timeCapsuleService.create(me.userId(), request));
    }
}
