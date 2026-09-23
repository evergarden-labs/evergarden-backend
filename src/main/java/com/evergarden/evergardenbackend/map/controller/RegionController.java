package com.evergarden.evergardenbackend.map.controller;

import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.response.PageMeta;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.map.dto.RegionDetail;
import com.evergarden.evergardenbackend.map.service.RegionService;
import com.evergarden.evergardenbackend.place.dto.PlaceSummary;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** MAP-03. 명세: {@code evergardenapi.yaml}의 {@code /regions/{regionCode}}. */
@RestController
@RequestMapping("/regions")
@RequiredArgsConstructor
@Validated
public class RegionController {

    private final RegionService regionService;

    @GetMapping("/{regionCode}")
    public ApiResponse<RegionDetail> getRegion(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable String regionCode) {
        return ApiResponse.of(regionService.getRegion(me.userId(), regionCode));
    }

    @GetMapping("/{regionCode}/places")
    public ApiResponse<List<PlaceSummary>> listRegionPlaces(
            @PathVariable String regionCode,
            @RequestParam(required = false) String contentTypeId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        Page<PlaceSummary> result = regionService.listRegionPlaces(regionCode, contentTypeId, PageRequest.of(page - 1, size));
        return ApiResponse.of(result.getContent(), PageMeta.from(result));
    }
}
