package com.evergarden.evergardenbackend.community.controller;

import com.evergarden.evergardenbackend.community.dto.CommentResponse;
import com.evergarden.evergardenbackend.community.dto.CommentWriteRequest;
import com.evergarden.evergardenbackend.community.service.CommentService;
import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** COMM-12·13. 명세: {@code evergardenapi.yaml}의 {@code /comments/{commentId}}. */
@RestController
@RequestMapping("/comments/{commentId}")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PatchMapping
    public ApiResponse<CommentResponse> update(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentWriteRequest request) {
        return ApiResponse.of(commentService.update(me.userId(), commentId, request));
    }

    @DeleteMapping
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long commentId) {
        commentService.delete(me.userId(), commentId);
        return ApiResponse.empty();
    }
}
