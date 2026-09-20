package com.evergarden.evergardenbackend.trip.controller;

import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.trip.dto.TripDetail;
import com.evergarden.evergardenbackend.trip.dto.TripPlaceCreateRequest;
import com.evergarden.evergardenbackend.trip.dto.TripPlaceOrderRequest;
import com.evergarden.evergardenbackend.trip.dto.TripPlaceResponse;
import com.evergarden.evergardenbackend.trip.dto.TripPlaceUpdateRequest;
import com.evergarden.evergardenbackend.trip.service.TripPlaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PLAN-02·04·08. 명세: {@code evergardenapi.yaml}의 {@code /trips/{tripId}/places} 계열. */
@RestController
@RequestMapping("/trips/{tripId}")
@RequiredArgsConstructor
public class TripPlaceController {

    private final TripPlaceService tripPlaceService;

    @PostMapping("/places")
    public ApiResponse<TripPlaceResponse> add(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long tripId,
            @Valid @RequestBody TripPlaceCreateRequest request) {
        return ApiResponse.of(tripPlaceService.addPlace(me.userId(), tripId, request));
    }

    @PatchMapping("/places/{tripPlaceId}")
    public ApiResponse<TripPlaceResponse> update(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long tripId,
            @PathVariable Long tripPlaceId,
            @Valid @RequestBody TripPlaceUpdateRequest request) {
        return ApiResponse.of(tripPlaceService.updatePlace(me.userId(), tripId, tripPlaceId, request));
    }

    @DeleteMapping("/places/{tripPlaceId}")
    public ApiResponse<Void> remove(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long tripId,
            @PathVariable Long tripPlaceId) {
        tripPlaceService.removePlace(me.userId(), tripId, tripPlaceId);
        return ApiResponse.empty();
    }

    @PutMapping("/places/order")
    public ApiResponse<TripDetail> replaceOrder(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long tripId,
            @Valid @RequestBody TripPlaceOrderRequest request) {
        return ApiResponse.of(tripPlaceService.replaceOrder(me.userId(), tripId, request));
    }
}
