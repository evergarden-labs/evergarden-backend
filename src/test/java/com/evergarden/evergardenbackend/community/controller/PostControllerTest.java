package com.evergarden.evergardenbackend.community.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.community.dto.PostDetail;
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

/** `@Valid` 검증과 서비스 예외 전달을 확인한다 — 트립 컨트롤러 테스트와 같은 이유로 {@code @WebMvcTest}를 쓴다. */
@ActiveProfiles("test")
@WebMvcTest(controllers = PostController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class PostControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean PostService postService;

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
        mvc.perform(post("/posts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"","tripId":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("코스도 아카이브도 없으면 서비스의 INVALID_SHARE_TARGET이 그대로 전달된다")
    void 공유대상_없음() throws Exception {
        given(postService.create(eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_SHARE_TARGET));

        mvc.perform(post("/posts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"내용"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_SHARE_TARGET"));
    }

    @Test
    @DisplayName("정상 요청은 로그인한 사용자 ID로 서비스에 위임한다")
    void 정상_작성() throws Exception {
        given(postService.create(eq(1L), any())).willReturn(mock(PostDetail.class));

        mvc.perform(post("/posts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"제주 여행 다녀왔어요","tripId":10}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 비로그인() throws Exception {
        mvc.perform(post("/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"내용","tripId":1}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ── 수정(COMM-05) ────────────────────────────────────────

    @Test
    @DisplayName("수정 시 content가 비어 있으면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 수정_빈본문() throws Exception {
        mvc.perform(patch("/posts/5")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("남의 게시물을 수정하면 서비스의 403이 그대로 전달된다")
    void 수정_남의게시물() throws Exception {
        given(postService.update(eq(1L), eq(5L), any()))
                .willThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER));

        mvc.perform(patch("/posts/5")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"고친 내용"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_RESOURCE_OWNER"));
    }

    @Test
    @DisplayName("정상 수정 요청은 로그인한 사용자 ID로 서비스에 위임한다")
    void 정상_수정() throws Exception {
        given(postService.update(eq(1L), eq(5L), any())).willReturn(mock(PostDetail.class));

        mvc.perform(patch("/posts/5")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"고친 내용"}
                                """))
                .andExpect(status().isOk());
    }

    // ── 삭제(COMM-06) ────────────────────────────────────────

    @Test
    @DisplayName("없는 게시물을 삭제하면 서비스의 404가 그대로 전달된다")
    void 삭제_없는게시물() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.POST_NOT_FOUND))
                .when(postService).delete(1L, 99L);

        mvc.perform(delete("/posts/99").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("삭제는 빈 성공 봉투를 돌려준다")
    void 삭제_성공() throws Exception {
        mvc.perform(delete("/posts/5").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
