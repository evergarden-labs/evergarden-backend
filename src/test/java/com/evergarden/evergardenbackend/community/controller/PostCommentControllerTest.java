package com.evergarden.evergardenbackend.community.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.community.dto.CommentResponse;
import com.evergarden.evergardenbackend.community.service.CommentService;
import com.evergarden.evergardenbackend.global.config.SecurityConfig;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.response.CursorMeta;
import com.evergarden.evergardenbackend.global.response.CursorPage;
import com.evergarden.evergardenbackend.global.security.DevUserProvider;
import com.evergarden.evergardenbackend.global.security.JwtAccessDeniedHandler;
import com.evergarden.evergardenbackend.global.security.JwtAuthenticationEntryPoint;
import com.evergarden.evergardenbackend.global.security.JwtAuthenticationFilter;
import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.global.security.SecurityErrorResponder;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** `@Valid` 검증과 서비스 예외 전달을 확인한다 — 다른 컨트롤러 테스트와 같은 이유로 {@code @WebMvcTest}를 쓴다. */
@ActiveProfiles("test")
@WebMvcTest(controllers = PostCommentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class PostCommentControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean CommentService commentService;

    String accessToken;

    @BeforeEach
    void setUp() {
        User activeUser = User.builder().nickname("여행자").build();
        given(userRepository.findById(any())).willReturn(Optional.of(activeUser));
        accessToken = tokenProvider.issueAccessToken(1L, Role.USER);
    }

    @Test
    @DisplayName("content가 비어 있으면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 빈_본문() throws Exception {
        mvc.perform(post("/posts/5/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("없는 게시물에 댓글을 달면 서비스의 404가 그대로 전달된다")
    void 없는_게시물() throws Exception {
        given(commentService.create(eq(1L), eq(99L), any()))
                .willThrow(new BusinessException(ErrorCode.POST_NOT_FOUND));

        mvc.perform(post("/posts/99/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"좋아요"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("정상 요청은 로그인한 사용자 ID로 서비스에 위임한다")
    void 정상_작성() throws Exception {
        given(commentService.create(eq(1L), eq(5L), any())).willReturn(mock(CommentResponse.class));

        mvc.perform(post("/posts/5/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"좋은 코스네요"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 비로그인() throws Exception {
        mvc.perform(post("/posts/5/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"좋은 코스네요"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ── 목록 조회(COMM-03) ───────────────────────────────────

    @Test
    @DisplayName("size가 50을 넘으면 INVALID_REQUEST")
    void 목록_크기초과() throws Exception {
        mvc.perform(get("/posts/5/comments?size=51").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("없는 게시물의 댓글을 조회하면 서비스의 404가 그대로 전달된다")
    void 목록_없는게시물() throws Exception {
        given(commentService.listComments(eq(99L), any(), anyInt()))
                .willThrow(new BusinessException(ErrorCode.POST_NOT_FOUND));

        mvc.perform(get("/posts/99/comments").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("정상 조회는 목록과 커서 메타를 그대로 돌려준다")
    void 목록_정상() throws Exception {
        given(commentService.listComments(eq(5L), any(), anyInt()))
                .willReturn(new CursorPage<>(java.util.List.of(mock(CommentResponse.class)), CursorMeta.last()));

        mvc.perform(get("/posts/5/comments").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.hasNext").value(false));
    }
}
