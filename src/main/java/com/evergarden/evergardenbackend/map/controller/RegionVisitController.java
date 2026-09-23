package com.evergarden.evergardenbackend.map.controller;

import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.map.dto.RegionVisitRequest;
import com.evergarden.evergardenbackend.map.dto.RegionVisitResult;
import com.evergarden.evergardenbackend.map.dto.RegionVisitStatus;
import com.evergarden.evergardenbackend.map.dto.VisitReward;
import com.evergarden.evergardenbackend.map.service.RegionVisitService;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** MAP-01·02, GARDEN-02. 명세: {@code evergardenapi.yaml}의 {@code /region-visits}·{@code /users/me/regions}. */
@RestController
@RequiredArgsConstructor
public class RegionVisitController {

    private final RegionVisitService regionVisitService;

    @PostMapping("/region-visits")
    public ApiResponse<RegionVisitResult> verifyRegionVisit(
            @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody RegionVisitRequest request) {
        return ApiResponse.of(regionVisitService.verify(me.userId(), request));
    }

    @GetMapping("/region-visits/{visitId}/reward")
    public ApiResponse<VisitReward> getVisitReward(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long visitId) {
        return ApiResponse.of(regionVisitService.getVisitReward(me.userId(), visitId));
    }

    @GetMapping("/users/me/regions")
    public ApiResponse<List<RegionVisitStatus>> listMyRegions(
            @AuthenticationPrincipal AuthPrincipal me,
            @RequestParam(defaultValue = "SIDO") RegionLevel level) {
        return ApiResponse.of(regionVisitService.listMyRegions(me.userId(), level));
    }
}
