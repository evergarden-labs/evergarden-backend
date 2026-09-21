package com.evergarden.evergardenbackend.community.controller;

import com.evergarden.evergardenbackend.community.dto.PostSortType;
import com.evergarden.evergardenbackend.community.dto.PostSummary;
import com.evergarden.evergardenbackend.community.service.PostService;
import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.response.CursorPage;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** COMM-02. 명세: {@code evergardenapi.yaml}의 {@code /regions/{regionCode}/posts}. */
@RestController
@RequestMapping("/regions/{regionCode}/posts")
@RequiredArgsConstructor
@Validated
public class RegionPostController {

    private final PostService postService;

    @GetMapping
    public ApiResponse<List<PostSummary>> list(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable String regionCode,
            @RequestParam(required = false) PostSortType sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        CursorPage<PostSummary> page = postService.listRegionPosts(me.userId(), regionCode, sort, cursor, size);
        return ApiResponse.of(page.items(), page.meta());
    }
}
