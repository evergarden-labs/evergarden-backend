package com.evergarden.evergardenbackend.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.admin.dto.AdminUserDetail;
import com.evergarden.evergardenbackend.admin.dto.AdminUserSummary;
import com.evergarden.evergardenbackend.admin.dto.SanctionResponse;
import com.evergarden.evergardenbackend.admin.service.AdminUserService;
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
import com.evergarden.evergardenbackend.report.entity.SanctionSource;
import com.evergarden.evergardenbackend.report.entity.SanctionType;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.entity.UserStatus;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDateTime;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 인증·권한·검증·서비스 위임을 확인한다. */
@ActiveProfiles("test")
@WebMvcTest(controllers = AdminUserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class AdminUserControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean AdminUserService adminUserService;

    String adminToken;

    @BeforeEach
    void setUp() {
        adminToken = tokenProvider.issueAccessToken(9L, Role.ADMIN);
    }

    private AdminUserSummary summary() {
        return new AdminUserSummary(1L, "여행자", UserStatus.ACTIVE, 0, LocalDateTime.now());
    }

    private SanctionResponse sanction(SanctionType type) {
        return new SanctionResponse(1L, 1L, type, SanctionSource.MANUAL, "사유", "담당자", LocalDateTime.now());
    }

    @Test
    @DisplayName("일반 회원 토큰이면 403 ADMIN_ONLY")
    void 목록_일반회원토큰_거절() throws Exception {
        given(userRepository.findById(any())).willReturn(Optional.of(User.builder().nickname("여행자").build()));
        String userToken = tokenProvider.issueAccessToken(1L, Role.USER);

        mvc.perform(get("/admin/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADMIN_ONLY"));
    }

    @Test
    @DisplayName("키워드·상태로 서비스에 위임한다")
    void 목록_정상() throws Exception {
        given(adminUserService.listUsers(eq("여행"), eq(UserStatus.ACTIVE), any()))
                .willReturn(new PageImpl<>(List.of(summary())));

        mvc.perform(get("/admin/users?keyword=여행&status=ACTIVE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nickname").value("여행자"))
                .andExpect(jsonPath("$.meta.page").value(1));
    }

    @Test
    @DisplayName("없는 회원이면 404 USER_NOT_FOUND")
    void 상세_없는회원() throws Exception {
        given(adminUserService.getUserDetail(99L)).willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mvc.perform(get("/admin/users/99").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("사유가 비어 있으면 경고 요청은 400 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 경고_사유없음() throws Exception {
        mvc.perform(post("/admin/users/1/warnings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("정상 경고는 로그인한 관리자 ID로 위임한다")
    void 경고_정상() throws Exception {
        given(adminUserService.warnUser(9L, 1L, "정책 위반")).willReturn(sanction(SanctionType.WARNING));

        mvc.perform(post("/admin/users/1/warnings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"정책 위반"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("WARNING"));
    }

    @Test
    @DisplayName("이미 차단된 회원이면 409 DUPLICATE_REQUEST")
    void 차단_중복() throws Exception {
        given(adminUserService.blockUser(9L, 1L, "약관 위반"))
                .willThrow(new BusinessException(ErrorCode.DUPLICATE_REQUEST));

        mvc.perform(post("/admin/users/1/blocks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"약관 위반"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_REQUEST"));
    }

    @Test
    @DisplayName("차단 해제는 AdminUserDetail을 돌려준다 — 다른 제재 오퍼레이션과 응답 모양이 다르다")
    void 차단해제_정상() throws Exception {
        given(adminUserService.unblockUser(9L, 1L, "이의 제기 수용"))
                .willReturn(new AdminUserDetail(1L, "여행자", UserStatus.ACTIVE, 0, LocalDateTime.now(),
                        List.of(), 0, 0, List.of(), null));

        mvc.perform(delete("/admin/users/1/blocks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"이의 제기 수용"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.sanctions").isArray());
    }
}
