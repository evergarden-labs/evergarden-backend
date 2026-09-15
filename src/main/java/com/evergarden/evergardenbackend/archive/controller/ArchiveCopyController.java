package com.evergarden.evergardenbackend.archive.controller;

import com.evergarden.evergardenbackend.archive.dto.ArchiveDetail;
import com.evergarden.evergardenbackend.archive.dto.ArchiveDuplicateRequest;
import com.evergarden.evergardenbackend.archive.dto.ArchiveImportRequest;
import com.evergarden.evergardenbackend.archive.service.ArchiveCopyService;
import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * ARCH-15·16. 명세: {@code evergardenapi.yaml}의 {@code /archives/{archiveId}/duplicate}와
 * {@code /posts/{postId}/archive/import} — 경로가 서로 달라 클래스 하나에 같이 둔다.
 */
@RestController
@RequiredArgsConstructor
public class ArchiveCopyController {

    private final ArchiveCopyService archiveCopyService;

    @PostMapping("/archives/{archiveId}/duplicate")
    public ApiResponse<ArchiveDetail> duplicate(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long archiveId,
            @Valid @RequestBody(required = false) ArchiveDuplicateRequest request) {
        String title = request != null ? request.title() : null;
        return ApiResponse.of(archiveCopyService.duplicate(me.userId(), archiveId, title));
    }

    @PostMapping("/posts/{postId}/archive/import")
    public ApiResponse<ArchiveDetail> importShared(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long postId,
            @Valid @RequestBody ArchiveImportRequest request) {
        return ApiResponse.of(archiveCopyService.importShared(me.userId(), postId, request.title()));
    }
}
