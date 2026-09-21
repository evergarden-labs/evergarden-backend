package com.evergarden.evergardenbackend.community.controller;

import com.evergarden.evergardenbackend.community.dto.PostSummary;
import com.evergarden.evergardenbackend.community.service.PostService;
import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.global.response.PageMeta;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.user.dto.PublicProfile;
import com.evergarden.evergardenbackend.user.service.UserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** COMM-08·09·20, ARCH-10. 명세: {@code evergardenapi.yaml}의 {@code /users}. */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final PostService postService;
    private final UserService userService;

    @GetMapping("/me/likes")
    public ApiResponse<List<PostSummary>> myLikedPosts(
            @AuthenticationPrincipal AuthPrincipal me,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        Page<PostSummary> result = postService.listMyLikedPosts(me.userId(), PageRequest.of(page - 1, size));
        return ApiResponse.of(result.getContent(), PageMeta.from(result));
    }

    @GetMapping("/me/posts")
    public ApiResponse<List<PostSummary>> myPosts(
            @AuthenticationPrincipal AuthPrincipal me,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        Page<PostSummary> result = postService.listMyPosts(me.userId(), PageRequest.of(page - 1, size));
        return ApiResponse.of(result.getContent(), PageMeta.from(result));
    }

    @GetMapping("/search")
    public ApiResponse<List<PublicProfile>> search(
            @RequestParam @NotBlank @Size(min = 1, max = 20) String nickname) {
        return ApiResponse.of(userService.search(nickname));
    }

    @GetMapping("/{userId}")
    public ApiResponse<PublicProfile> getProfile(@PathVariable Long userId) {
        return ApiResponse.of(userService.getProfile(userId));
    }

    @GetMapping("/{userId}/posts")
    public ApiResponse<List<PostSummary>> userPosts(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        Page<PostSummary> result = postService.listUserPosts(userId, me.userId(), PageRequest.of(page - 1, size));
        return ApiResponse.of(result.getContent(), PageMeta.from(result));
    }
}
