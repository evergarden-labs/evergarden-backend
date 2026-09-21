package com.evergarden.evergardenbackend.community.controller;

import com.evergarden.evergardenbackend.community.dto.CommentWriteRequest;
import com.evergarden.evergardenbackend.community.dto.Reply;
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

/** COMM-15·16. 명세: {@code evergardenapi.yaml}의 {@code /replies/{replyId}}. */
@RestController
@RequestMapping("/replies/{replyId}")
@RequiredArgsConstructor
public class ReplyController {

    private final CommentService commentService;

    @PatchMapping
    public ApiResponse<Reply> update(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long replyId,
            @Valid @RequestBody CommentWriteRequest request) {
        return ApiResponse.of(commentService.updateReply(me.userId(), replyId, request));
    }

    @DeleteMapping
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long replyId) {
        commentService.deleteReply(me.userId(), replyId);
        return ApiResponse.empty();
    }
}
