package com.evergarden.evergardenbackend.garden.controller;

import com.evergarden.evergardenbackend.garden.dto.Garden;
import com.evergarden.evergardenbackend.garden.service.GardenService;
import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GARDEN-01. 명세: {@code evergardenapi.yaml}의 {@code /garden}. */
@RestController
@RequiredArgsConstructor
public class GardenController {

    private final GardenService gardenService;

    @GetMapping("/garden")
    public ApiResponse<Garden> getMyGarden(@AuthenticationPrincipal AuthPrincipal me) {
        return ApiResponse.of(gardenService.getMyGarden(me.userId()));
    }
}
