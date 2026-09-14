package com.evergarden.evergardenbackend.media.controller;

import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.media.dto.MediaCompleteRequest;
import com.evergarden.evergardenbackend.media.dto.MediaResponse;
import com.evergarden.evergardenbackend.media.dto.MediaUploadTicket;
import com.evergarden.evergardenbackend.media.dto.MediaUploadUrlsRequest;
import com.evergarden.evergardenbackend.media.service.MediaService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MEDIA-01. 명세: {@code evergardenapi.yaml}의 {@code /media/*}. */
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/upload-urls")
    public ApiResponse<List<MediaUploadTicket>> issueUploadUrls(
            @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody MediaUploadUrlsRequest request) {
        return ApiResponse.of(mediaService.issueUploadUrls(me.userId(), request.files()));
    }

    @PostMapping("/complete")
    public ApiResponse<List<MediaResponse>> completeUpload(
            @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody MediaCompleteRequest request) {
        return ApiResponse.of(mediaService.completeUpload(me.userId(), request.mediaIds()));
    }
}
