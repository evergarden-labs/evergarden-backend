package com.evergarden.evergardenbackend.admin.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.admin.dto.AdminSession;
import com.evergarden.evergardenbackend.admin.service.AdminAuthService;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** 인증 필요·검증 실패·서비스 위임·역할 검사를 확인한다. */
@ActiveProfiles("test")
@WebMvcTest(controllers = AdminAuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class AdminAuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean AdminAuthService adminAuthService;

    @Test
    @DisplayName("아이디·비밀번호가 비어 있으면 400 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 로그인_빈값() throws Exception {
        mvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("정상 로그인은 서비스로 위임하고 결과를 그대로 돌려준다")
    void 로그인_정상() throws Exception {
        given(adminAuthService.login("ops", "pw"))
                .willReturn(new AdminSession("access-token", null, "담당자"));

        mvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"ops","password":"pw"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.adminName").value("담당자"));
    }

    @Test
    @DisplayName("서비스가 던진 ADMIN_CREDENTIALS_INVALID가 그대로 전달된다")
    void 로그인_자격증명실패() throws Exception {
        given(adminAuthService.login(eq("ops"), eq("wrong")))
                .willThrow(new BusinessException(ErrorCode.ADMIN_CREDENTIALS_INVALID));

        mvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"ops","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("ADMIN_CREDENTIALS_INVALID"));
    }

    @Test
    @DisplayName("로그인 없이 로그아웃을 부르면 401")
    void 로그아웃_비로그인() throws Exception {
        mvc.perform(post("/admin/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("일반 회원 토큰으로 로그아웃을 부르면 403 ADMIN_ONLY — hasRole(ADMIN)이 실제로 막는다")
    void 로그아웃_일반회원토큰_거절() throws Exception {
        User activeUser = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(activeUser, "id", 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(activeUser));
        String userToken = tokenProvider.issueAccessToken(1L, Role.USER);

        mvc.perform(post("/admin/auth/logout").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADMIN_ONLY"));
    }

    @Test
    @DisplayName("관리자 토큰으로 로그아웃하면 200 — 폐기할 토큰이 없어도 정상 처리")
    void 로그아웃_정상() throws Exception {
        String adminToken = tokenProvider.issueAccessToken(1L, Role.ADMIN);

        mvc.perform(post("/admin/auth/logout").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
