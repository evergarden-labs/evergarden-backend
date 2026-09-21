package com.evergarden.evergardenbackend.community.controller;

import com.evergarden.evergardenbackend.community.dto.CommentWriteRequest;
import com.evergarden.evergardenbackend.community.dto.Reply;
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

/** COMM-14. 명세: {@code evergardenapi.yaml}의 {@code /comments/{commentId}/replies}. */
@RestController
@RequestMapping("/comments/{commentId}/replies")
@RequiredArgsConstructor
public class CommentReplyController {

    private final CommentService commentService;

    @PostMapping
    public ApiResponse<Reply> create(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentWriteRequest request) {
        return ApiResponse.of(commentService.createReply(me.userId(), commentId, request));
    }
}
