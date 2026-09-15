package com.evergarden.evergardenbackend.archive.controller;

import com.evergarden.evergardenbackend.archive.dto.ArchiveDetail;
import com.evergarden.evergardenbackend.archive.dto.CollaborationSession;
import com.evergarden.evergardenbackend.archive.dto.CollaboratorInviteRequest;
import com.evergarden.evergardenbackend.archive.dto.CollaboratorResponse;
import com.evergarden.evergardenbackend.archive.service.ArchiveCollaborationService;
import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ARCH-10·11·13·14·18. 명세: {@code evergardenapi.yaml}의 공동 편집 오퍼레이션들. */
@RestController
@RequestMapping("/archives/{archiveId}")
@RequiredArgsConstructor
public class ArchiveCollaborationController {

    private final ArchiveCollaborationService collaborationService;

    @GetMapping("/collaboration/session")
    public ApiResponse<CollaborationSession> getSession(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long archiveId) {
        return ApiResponse.of(collaborationService.getSession(me.userId(), archiveId));
    }

    @PostMapping("/collaborators")
    public ApiResponse<CollaboratorResponse> invite(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long archiveId,
            @Valid @RequestBody CollaboratorInviteRequest request) {
        return ApiResponse.of(collaborationService.invite(me.userId(), archiveId, request.userId()));
    }

    @PostMapping("/collaborators/me/accept")
    public ApiResponse<CollaboratorResponse> accept(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long archiveId) {
        return ApiResponse.of(collaborationService.accept(me.userId(), archiveId));
    }

    @PostMapping("/collaborators/me/decline")
    public ApiResponse<Void> decline(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long archiveId) {
        collaborationService.decline(me.userId(), archiveId);
        return ApiResponse.empty();
    }

    @DeleteMapping("/collaborators/me")
    public ApiResponse<Void> leave(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long archiveId) {
        collaborationService.leave(me.userId(), archiveId);
        return ApiResponse.empty();
    }

    @PostMapping("/collaboration/close")
    public ApiResponse<ArchiveDetail> close(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long archiveId) {
        return ApiResponse.of(collaborationService.close(me.userId(), archiveId));
    }
}
