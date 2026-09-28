package com.evergarden.evergardenbackend.admin.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.admin.dto.AdminDashboard;
import com.evergarden.evergardenbackend.admin.service.AdminDashboardService;
import com.evergarden.evergardenbackend.global.config.SecurityConfig;
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
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 인증 필요·서비스 위임을 확인한다. */
@ActiveProfiles("test")
@WebMvcTest(controllers = AdminDashboardController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class AdminDashboardControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean AdminDashboardService adminDashboardService;

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 대시보드_비로그인() throws Exception {
        mvc.perform(get("/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("일반 회원 토큰이면 403 ADMIN_ONLY — hasRole(ADMIN)이 실제로 막는다")
    void 대시보드_일반회원토큰_거절() throws Exception {
        given(userRepository.findById(ArgumentMatchers.any()))
                .willReturn(Optional.of(User.builder().nickname("여행자").build()));
        String userToken = tokenProvider.issueAccessToken(1L, Role.USER);

        mvc.perform(get("/admin/dashboard").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADMIN_ONLY"));
    }

    @Test
    @DisplayName("관리자 토큰으로 조회하면 서비스 결과를 그대로 돌려준다")
    void 대시보드_정상() throws Exception {
        given(adminDashboardService.getDashboard())
                .willReturn(new AdminDashboard(100, 3, 5, 7, 40, 50, 200));
        String adminToken = tokenProvider.issueAccessToken(1L, Role.ADMIN);

        mvc.perform(get("/admin/dashboard").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUserCount").value(100))
                .andExpect(jsonPath("$.data.pendingReportCount").value(5))
                .andExpect(jsonPath("$.meta").isEmpty());
    }
}
