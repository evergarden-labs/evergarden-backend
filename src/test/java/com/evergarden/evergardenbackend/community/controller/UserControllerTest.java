package com.evergarden.evergardenbackend.community.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.community.dto.PostSummary;
import com.evergarden.evergardenbackend.community.service.PostService;
import com.evergarden.evergardenbackend.global.config.SecurityConfig;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.security.DevUserProvider;
import com.evergarden.evergardenbackend.global.security.JwtAccessDeniedHandler;
import com.evergarden.evergardenbackend.global.security.JwtAuthenticationEntryPoint;
import com.evergarden.evergardenbackend.global.security.JwtAuthenticationFilter;
import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.global.security.SecurityErrorResponder;
import com.evergarden.evergardenbackend.user.dto.PublicProfile;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import com.evergarden.evergardenbackend.user.service.UserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** `@Min`/`@Max` 검증과 로그인 사용자 위임을 확인한다 — 다른 컨트롤러 테스트와 같은 이유로 {@code @WebMvcTest}를 쓴다. */
@ActiveProfiles("test")
@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class UserControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean PostService postService;
    @MockitoBean UserService userService;

    String accessToken;

    @BeforeEach
    void setUp() {
        User activeUser = User.builder().nickname("여행자").build();
        given(userRepository.findById(any())).willReturn(Optional.of(activeUser));
        accessToken = tokenProvider.issueAccessToken(1L, Role.USER);
    }

    @Test
    @DisplayName("좋아요 목록은 로그인한 사용자 ID로 서비스에 위임한다")
    void 좋아요목록_조회() throws Exception {
        given(postService.listMyLikedPosts(eq(1L), any()))
                .willReturn(new PageImpl<>(List.of(org.mockito.Mockito.mock(PostSummary.class))));

        mvc.perform(get("/users/me/likes").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("size가 50을 넘으면 INVALID_REQUEST")
    void 좋아요목록_크기초과() throws Exception {
        mvc.perform(get("/users/me/likes?size=51").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 비로그인() throws Exception {
        mvc.perform(get("/users/me/likes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내 게시물 목록은 로그인한 사용자 ID로 서비스에 위임한다")
    void 내게시물목록_조회() throws Exception {
        given(postService.listMyPosts(eq(1L), any()))
                .willReturn(new PageImpl<>(List.of(org.mockito.Mockito.mock(PostSummary.class))));

        mvc.perform(get("/users/me/posts").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("내 게시물 목록도 size가 50을 넘으면 INVALID_REQUEST")
    void 내게시물목록_크기초과() throws Exception {
        mvc.perform(get("/users/me/posts?size=51").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── 닉네임 검색(ARCH-10) ─────────────────────────────────

    @Test
    @DisplayName("nickname이 비어 있으면 INVALID_REQUEST")
    void 검색_빈닉네임() throws Exception {
        mvc.perform(get("/users/search?nickname=").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("정상 검색은 서비스 결과를 그대로 돌려준다 — /users/{userId}와 안 겹친다")
    void 검색_정상() throws Exception {
        given(userService.search("여행자9183"))
                .willReturn(List.of(new PublicProfile(5L, "여행자9183", null, 3)));

        mvc.perform(get("/users/search?nickname=여행자9183").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(5));
    }

    // ── 작성자 프로필 조회(COMM-20) ──────────────────────────

    @Test
    @DisplayName("없는 사용자를 조회하면 서비스의 404가 그대로 전달된다")
    void 프로필_없는사용자() throws Exception {
        given(userService.getProfile(99L)).willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mvc.perform(get("/users/99").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("정상 조회는 서비스 결과를 그대로 돌려준다")
    void 프로필_정상() throws Exception {
        given(userService.getProfile(5L)).willReturn(new PublicProfile(5L, "여행자", null, 2));

        mvc.perform(get("/users/5").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("여행자"));
    }

    // ── 작성자의 게시물 목록 조회(COMM-20) ───────────────────

    @Test
    @DisplayName("없는 작성자의 게시물을 조회하면 서비스의 404가 그대로 전달된다")
    void 작성자게시물_없는사용자() throws Exception {
        given(postService.listUserPosts(eq(99L), eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mvc.perform(get("/users/99/posts").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("정상 조회는 작성자 id와 보는 사람 id를 구분해서 서비스에 위임한다")
    void 작성자게시물_정상() throws Exception {
        given(postService.listUserPosts(eq(5L), eq(1L), any()))
                .willReturn(new PageImpl<>(List.of(org.mockito.Mockito.mock(PostSummary.class))));

        mvc.perform(get("/users/5/posts").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
