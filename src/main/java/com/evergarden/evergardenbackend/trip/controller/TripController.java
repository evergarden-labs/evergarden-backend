package com.evergarden.evergardenbackend.trip.controller;

import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.response.PageMeta;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.place.dto.PlaceSummary;
import com.evergarden.evergardenbackend.trip.dto.AutoArrangeRequest;
import com.evergarden.evergardenbackend.trip.dto.AutoArrangeResult;
import com.evergarden.evergardenbackend.trip.dto.TripCreateRequest;
import com.evergarden.evergardenbackend.trip.dto.TripDetail;
import com.evergarden.evergardenbackend.trip.dto.TripRoute;
import com.evergarden.evergardenbackend.trip.dto.TripSummary;
import com.evergarden.evergardenbackend.trip.dto.TripUpdateRequest;
import com.evergarden.evergardenbackend.trip.service.TripAutoArrangeService;
import com.evergarden.evergardenbackend.trip.service.TripNearbyPlaceService;
import com.evergarden.evergardenbackend.trip.service.TripRouteService;
import com.evergarden.evergardenbackend.trip.service.TripService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** PLAN-01·03·04·05·08·09·10·11. 명세: {@code evergardenapi.yaml}의 {@code /trips}. */
@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
@Validated
public class TripController {

    private final TripService tripService;
    private final TripRouteService tripRouteService;
    private final TripAutoArrangeService tripAutoArrangeService;
    private final TripNearbyPlaceService tripNearbyPlaceService;

    @PostMapping
    public ApiResponse<TripDetail> create(
            @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody TripCreateRequest request) {
        return ApiResponse.of(tripService.create(me.userId(), request));
    }

    @GetMapping
    public ApiResponse<List<TripSummary>> list(
            @AuthenticationPrincipal AuthPrincipal me,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        Page<TripSummary> result = tripService.list(me.userId(), PageRequest.of(page - 1, size));
        return ApiResponse.of(result.getContent(), PageMeta.from(result));
    }

    @GetMapping("/{tripId}")
    public ApiResponse<TripDetail> get(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long tripId) {
        return ApiResponse.of(tripService.get(me.userId(), tripId));
    }

    @PatchMapping("/{tripId}")
    public ApiResponse<TripDetail> update(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long tripId,
            @Valid @RequestBody TripUpdateRequest request) {
        return ApiResponse.of(tripService.update(me.userId(), tripId, request));
    }

    @DeleteMapping("/{tripId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long tripId) {
        tripService.delete(me.userId(), tripId);
        return ApiResponse.empty();
    }

    @GetMapping("/{tripId}/route")
    public ApiResponse<TripRoute> route(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long tripId) {
        return ApiResponse.of(tripRouteService.getRoute(me.userId(), tripId));
    }

    @PostMapping("/{tripId}/auto-arrange")
    public ApiResponse<AutoArrangeResult> autoArrange(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long tripId,
            @RequestBody(required = false) AutoArrangeRequest request) {
        return ApiResponse.of(tripAutoArrangeService.propose(me.userId(), tripId, request));
    }

    @GetMapping("/{tripId}/nearby-places")
    public ApiResponse<List<PlaceSummary>> nearbyPlaces(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long tripId,
            @RequestParam(required = false) @Min(1) Short dayNumber,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        Page<PlaceSummary> result = tripNearbyPlaceService.listNearby(
                me.userId(), tripId, dayNumber, PageRequest.of(page - 1, size));
        return ApiResponse.of(result.getContent(), PageMeta.from(result));
    }
}
