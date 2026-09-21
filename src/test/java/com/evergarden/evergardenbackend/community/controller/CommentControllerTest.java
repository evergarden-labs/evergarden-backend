package com.evergarden.evergardenbackend.community.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.community.dto.CommentResponse;
import com.evergarden.evergardenbackend.community.service.CommentService;
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

/** `@Valid` 검증과 서비스 예외 전달을 확인한다 — 다른 컨트롤러 테스트와 같은 이유로 {@code @WebMvcTest}를 쓴다. */
@ActiveProfiles("test")
@WebMvcTest(controllers = CommentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class CommentControllerTest {

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
    @DisplayName("수정 시 content가 비어 있으면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 수정_빈본문() throws Exception {
        mvc.perform(patch("/comments/10")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("남의 댓글을 수정하면 서비스의 403이 그대로 전달된다")
    void 수정_남의댓글() throws Exception {
        given(commentService.update(eq(1L), eq(10L), any()))
                .willThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER));

        mvc.perform(patch("/comments/10")
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
        given(commentService.update(eq(1L), eq(10L), any())).willReturn(mock(CommentResponse.class));

        mvc.perform(patch("/comments/10")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"고친 내용"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("없는 댓글을 삭제하면 서비스의 404가 그대로 전달된다")
    void 삭제_없는댓글() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.COMMENT_NOT_FOUND))
                .when(commentService).delete(1L, 99L);

        mvc.perform(delete("/comments/99").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("삭제는 빈 성공 봉투를 돌려준다")
    void 삭제_성공() throws Exception {
        mvc.perform(delete("/comments/10").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
