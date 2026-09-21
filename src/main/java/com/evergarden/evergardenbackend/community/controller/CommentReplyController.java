package com.evergarden.evergardenbackend.community.controller;

import com.evergarden.evergardenbackend.community.dto.CommentWriteRequest;
import com.evergarden.evergardenbackend.community.dto.Reply;
import com.evergarden.evergardenbackend.community.service.CommentService;
import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.response.CursorPage;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** COMM-03·14. 명세: {@code evergardenapi.yaml}의 {@code /comments/{commentId}/replies}. */
@RestController
@RequestMapping("/comments/{commentId}/replies")
@RequiredArgsConstructor
@Validated
public class CommentReplyController {

    private final CommentService commentService;

    @GetMapping
    public ApiResponse<List<Reply>> list(
            @PathVariable Long commentId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        CursorPage<Reply> page = commentService.listReplies(commentId, cursor, size);
        return ApiResponse.of(page.items(), page.meta());
    }

    @PostMapping
    public ApiResponse<Reply> create(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentWriteRequest request) {
        return ApiResponse.of(commentService.createReply(me.userId(), commentId, request));
    }
}
