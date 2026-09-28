package com.evergarden.evergardenbackend.admin.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.admin.service.AdminContentService;
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
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 인증·검증·서비스 위임을 확인한다. */
@ActiveProfiles("test")
@WebMvcTest(controllers = AdminContentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class AdminContentControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean AdminContentService adminContentService;

    String adminToken;

    @BeforeEach
    void setUp() {
        adminToken = tokenProvider.issueAccessToken(9L, Role.ADMIN);
    }

    @Test
    @DisplayName("사유 없이 게시물 삭제를 시도하면 400 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 게시물삭제_사유없음() throws Exception {
        mvc.perform(delete("/admin/posts/1")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("정상 게시물 삭제는 로그인한 관리자 ID로 위임한다")
    void 게시물삭제_정상() throws Exception {
        mvc.perform(delete("/admin/posts/1")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"정책 위반"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("서비스가 던진 POST_NOT_FOUND가 그대로 전달된다")
    void 게시물삭제_없음_전달() throws Exception {
        willThrow(new BusinessException(ErrorCode.POST_NOT_FOUND))
                .given(adminContentService).deletePost(9L, 99L, "정책 위반");

        mvc.perform(delete("/admin/posts/99")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"정책 위반"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("정상 댓글 삭제는 로그인한 관리자 ID로 위임한다")
    void 댓글삭제_정상() throws Exception {
        mvc.perform(delete("/admin/comments/2")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"욕설"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("대댓글 삭제도 댓글과 같은 서비스 메서드로 위임한다")
    void 대댓글삭제_정상() throws Exception {
        mvc.perform(delete("/admin/replies/3")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"욕설"}
                                """))
                .andExpect(status().isOk());

        Mockito.verify(adminContentService).deleteComment(9L, 3L, "욕설");
    }

    @Test
    @DisplayName("일반 회원 토큰이면 403 ADMIN_ONLY")
    void 삭제_일반회원토큰_거절() throws Exception {
        given(userRepository.findById(ArgumentMatchers.any())).willReturn(
                Optional.of(User.builder().nickname("여행자").build()));
        String userToken = tokenProvider.issueAccessToken(1L, Role.USER);

        mvc.perform(delete("/admin/posts/1")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"정책 위반"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADMIN_ONLY"));
    }
}
