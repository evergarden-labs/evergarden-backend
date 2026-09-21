package com.evergarden.evergardenbackend.community.controller;

import com.evergarden.evergardenbackend.community.dto.PostCreateRequest;
import com.evergarden.evergardenbackend.community.dto.PostDetail;
import com.evergarden.evergardenbackend.community.service.PostService;
import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** COMM-01 이하. 명세: {@code evergardenapi.yaml}의 {@code /posts}. */
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ApiResponse<PostDetail> create(
            @AuthenticationPrincipal AuthPrincipal me,
            @Valid @RequestBody PostCreateRequest request) {
        return ApiResponse.of(postService.create(me.userId(), request));
    }
}
