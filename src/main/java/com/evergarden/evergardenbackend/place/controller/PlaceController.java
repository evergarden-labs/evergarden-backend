package com.evergarden.evergardenbackend.place.controller;

import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.response.PageMeta;
import com.evergarden.evergardenbackend.place.dto.PlaceDetail;
import com.evergarden.evergardenbackend.place.dto.PlaceSummary;
import com.evergarden.evergardenbackend.place.service.PlaceQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** PLAN-06·07. 명세: {@code evergardenapi.yaml}의 {@code /places}. */
@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
@Validated
public class PlaceController {

    private final PlaceQueryService placeQueryService;

    @GetMapping("/search")
    public ApiResponse<List<PlaceSummary>> search(
            @RequestParam(required = false) @Size(min = 1, max = 50) String keyword,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String contentTypeId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        Page<PlaceSummary> result = placeQueryService.search(
                keyword, regionCode, contentTypeId, PageRequest.of(page - 1, size));
        return ApiResponse.of(result.getContent(), PageMeta.from(result));
    }

    @GetMapping("/{placeId}")
    public ApiResponse<PlaceDetail> getPlace(@PathVariable Long placeId) {
        return ApiResponse.of(placeQueryService.getPlace(placeId));
    }
}
