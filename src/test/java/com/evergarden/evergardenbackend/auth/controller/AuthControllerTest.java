package com.evergarden.evergardenbackend.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.auth.dto.AuthResult;
import com.evergarden.evergardenbackend.auth.dto.TokenPair;
import com.evergarden.evergardenbackend.auth.service.AuthService;
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

/** `@Valid` 검증·보안 설정(공개/인증 필요)·서비스 위임을 확인한다. */
@ActiveProfiles("test")
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean AuthService authService;

    String accessToken;

    @BeforeEach
    void setUp() {
        User activeUser = User.builder().nickname("여행자").build();
        given(userRepository.findById(any())).willReturn(Optional.of(activeUser));
        accessToken = tokenProvider.issueAccessToken(1L, Role.USER);
    }

    // ── 소셜 로그인 (security: []) ────────────────────────────

    @Test
    @DisplayName("토큰 없이도 호출된다 — security: []")
    void 소셜로그인_비로그인_허용() throws Exception {
        given(authService.loginWithSocial(eq("google"), eq("sdk-token")))
                .willReturn(new AuthResult("access", "refresh", true, false));

        mvc.perform(post("/auth/social/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"socialAccessToken":"sdk-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access"));
    }

    @Test
    @DisplayName("socialAccessToken이 비어 있으면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 소셜로그인_토큰_비어있음() throws Exception {
        mvc.perform(post("/auth/social/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("서비스가 던진 SOCIAL_AUTH_FAILED가 그대로 전달된다")
    void 소셜로그인_검증실패() throws Exception {
        given(authService.loginWithSocial(eq("google"), eq("bad-token")))
                .willThrow(new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED));

        mvc.perform(post("/auth/social/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"socialAccessToken":"bad-token"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SOCIAL_AUTH_FAILED"));
    }

    // ── 계정 복구 (security: []) ──────────────────────────────

    @Test
    @DisplayName("토큰 없이도 호출된다 — security: []")
    void 계정복구_비로그인_허용() throws Exception {
        given(authService.restoreAccount(eq("google"), eq("sdk-token")))
                .willReturn(new AuthResult("access", "refresh", false, true));

        mvc.perform(post("/auth/restore/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"socialAccessToken":"sdk-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false))
                .andExpect(jsonPath("$.data.accessToken").value("access"));
    }

    @Test
    @DisplayName("socialAccessToken이 비어 있으면 INVALID_REQUEST")
    void 계정복구_토큰_비어있음() throws Exception {
        mvc.perform(post("/auth/restore/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("서비스가 던진 RESTORE_PERIOD_EXPIRED가 그대로 전달된다")
    void 계정복구_유예만료() throws Exception {
        given(authService.restoreAccount(eq("google"), eq("sdk-token")))
                .willThrow(new BusinessException(ErrorCode.RESTORE_PERIOD_EXPIRED));

        mvc.perform(post("/auth/restore/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"socialAccessToken":"sdk-token"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("RESTORE_PERIOD_EXPIRED"));
    }

    // ── 토큰 재발급 (security: []) ────────────────────────────

    @Test
    @DisplayName("토큰 없이도 호출된다 — security: []")
    void 재발급_비로그인_허용() throws Exception {
        given(authService.refreshToken("old-refresh")).willReturn(new TokenPair("new-access", "new-refresh"));

        mvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"old-refresh"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access"));
    }

    @Test
    @DisplayName("refreshToken이 비어 있으면 INVALID_REQUEST")
    void 재발급_토큰_비어있음() throws Exception {
        mvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── 로그아웃 (인증 필요) ───────────────────────────────────

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 로그아웃_비로그인() throws Exception {
        mvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인한 사용자 ID로 서비스에 위임한다")
    void 로그아웃_정상() throws Exception {
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        verify(authService).logout(1L);
    }

    // ── 회원 탈퇴 (인증 필요) ──────────────────────────────────

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 탈퇴_비로그인() throws Exception {
        mvc.perform(delete("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인한 사용자 ID로 서비스에 위임한다")
    void 탈퇴_정상() throws Exception {
        mvc.perform(delete("/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        verify(authService).withdraw(1L);
    }
}
