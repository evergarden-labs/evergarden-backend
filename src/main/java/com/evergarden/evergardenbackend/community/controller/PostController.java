package com.evergarden.evergardenbackend.community.controller;

import com.evergarden.evergardenbackend.community.dto.LikeResult;
import com.evergarden.evergardenbackend.community.dto.PostCreateRequest;
import com.evergarden.evergardenbackend.community.dto.PostDetail;
import com.evergarden.evergardenbackend.community.dto.PostSortType;
import com.evergarden.evergardenbackend.community.dto.PostSummary;
import com.evergarden.evergardenbackend.community.dto.PostUpdateRequest;
import com.evergarden.evergardenbackend.community.service.PostService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** COMM-01 이하. 명세: {@code evergardenapi.yaml}의 {@code /posts}. */
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
@Validated
public class PostController {

    private final PostService postService;

    @GetMapping
    public ApiResponse<List<PostSummary>> list(
            @AuthenticationPrincipal AuthPrincipal me,
            @RequestParam(required = false) PostSortType sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        CursorPage<PostSummary> page = postService.listPosts(me.userId(), sort, cursor, size);
        return ApiResponse.of(page.items(), page.meta());
    }

    @PostMapping
    public ApiResponse<PostDetail> create(
            @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody PostCreateRequest request) {
        return ApiResponse.of(postService.create(me.userId(), request));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostDetail> get(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long postId) {
        return ApiResponse.of(postService.get(me.userId(), postId));
    }

    @PatchMapping("/{postId}")
    public ApiResponse<PostDetail> update(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request) {
        return ApiResponse.of(postService.update(me.userId(), postId, request));
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long postId) {
        postService.delete(me.userId(), postId);
        return ApiResponse.empty();
    }

    @PostMapping("/{postId}/like")
    public ApiResponse<LikeResult> like(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long postId) {
        return ApiResponse.of(postService.like(me.userId(), postId));
    }

    @DeleteMapping("/{postId}/like")
    public ApiResponse<LikeResult> unlike(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long postId) {
        return ApiResponse.of(postService.unlike(me.userId(), postId));
    }
}
