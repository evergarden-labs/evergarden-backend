package com.evergarden.evergardenbackend.map.controller;

import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.map.dto.RegionVisitStatus;
import com.evergarden.evergardenbackend.map.service.RegionVisitService;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** MAP-02. 명세: {@code evergardenapi.yaml}의 {@code /users/me/regions}. */
@RestController
@RequiredArgsConstructor
public class RegionVisitController {

    private final RegionVisitService regionVisitService;

    @GetMapping("/users/me/regions")
    public ApiResponse<List<RegionVisitStatus>> listMyRegions(
            @AuthenticationPrincipal AuthPrincipal me,
            @RequestParam(defaultValue = "SIDO") RegionLevel level) {
        return ApiResponse.of(regionVisitService.listMyRegions(me.userId(), level));
    }
}
