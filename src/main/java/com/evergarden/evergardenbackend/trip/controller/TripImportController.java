package com.evergarden.evergardenbackend.trip.controller;

import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.trip.dto.ImportCourseRequest;
import com.evergarden.evergardenbackend.trip.dto.TripDetail;
import com.evergarden.evergardenbackend.trip.service.TripImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PLAN-12. 명세: {@code evergardenapi.yaml}의 {@code /posts/{postId}/course/import}. */
@RestController
@RequestMapping("/posts/{postId}/course")
@RequiredArgsConstructor
public class TripImportController {

    private final TripImportService tripImportService;

    @PostMapping("/import")
    public ApiResponse<TripDetail> importCourse(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long postId,
            @Valid @RequestBody(required = false) ImportCourseRequest request) {
        return ApiResponse.of(tripImportService.importCourse(me.userId(), postId, request));
    }
}
