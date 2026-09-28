package com.evergarden.evergardenbackend.admin.controller;

import com.evergarden.evergardenbackend.admin.dto.SanctionRequest;
import com.evergarden.evergardenbackend.admin.service.AdminContentService;
import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN-06·07·08. 명세: {@code evergardenapi.yaml}의 콘텐츠 강제 삭제 오퍼레이션들. */
@RestController
@RequiredArgsConstructor
public class AdminContentController {

    private final AdminContentService adminContentService;

    @DeleteMapping("/admin/posts/{postId}")
    public ApiResponse<Void> adminDeletePost(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long postId,
            @Valid @RequestBody SanctionRequest request) {
        adminContentService.deletePost(me.userId(), postId, request.reason());
        return ApiResponse.empty();
    }

    @DeleteMapping("/admin/comments/{commentId}")
    public ApiResponse<Void> adminDeleteComment(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long commentId,
            @Valid @RequestBody SanctionRequest request) {
        adminContentService.deleteComment(me.userId(), commentId, request.reason());
        return ApiResponse.empty();
    }

    /** 저장이 댓글과 같은 테이블이라 {@link #adminDeleteComment}와 로직이 완전히 같다. */
    @DeleteMapping("/admin/replies/{replyId}")
    public ApiResponse<Void> adminDeleteReply(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long replyId,
            @Valid @RequestBody SanctionRequest request) {
        adminContentService.deleteComment(me.userId(), replyId, request.reason());
        return ApiResponse.empty();
    }
}
