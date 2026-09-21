package com.evergarden.evergardenbackend.community.controller;

import com.evergarden.evergardenbackend.community.dto.CommentResponse;
import com.evergarden.evergardenbackend.community.dto.CommentWriteRequest;
import com.evergarden.evergardenbackend.community.service.CommentService;
import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** COMM-11 이하. 명세: {@code evergardenapi.yaml}의 {@code /posts/{postId}/comments}. */
@RestController
@RequestMapping("/posts/{postId}/comments")
@RequiredArgsConstructor
public class PostCommentController {

    private final CommentService commentService;

    @PostMapping
    public ApiResponse<CommentResponse> create(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long postId,
            @Valid @RequestBody CommentWriteRequest request) {
        return ApiResponse.of(commentService.create(me.userId(), postId, request));
    }
}
