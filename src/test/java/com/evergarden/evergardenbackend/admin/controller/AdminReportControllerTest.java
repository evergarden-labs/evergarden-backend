package com.evergarden.evergardenbackend.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.admin.dto.AdminReportDetail;
import com.evergarden.evergardenbackend.admin.dto.AdminReportSummary;
import com.evergarden.evergardenbackend.admin.dto.ReportReviewResult;
import com.evergarden.evergardenbackend.admin.service.AdminReportService;
import com.evergarden.evergardenbackend.community.dto.Author;
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
import com.evergarden.evergardenbackend.report.entity.ReportReason;
import com.evergarden.evergardenbackend.report.entity.ReportStatus;
import com.evergarden.evergardenbackend.report.entity.ReportTargetType;
import com.evergarden.evergardenbackend.user.entity.User;
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

/** 인증·검증·서비스 위임을 확인한다. */
@ActiveProfiles("test")
@WebMvcTest(controllers = AdminReportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class AdminReportControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean AdminReportService adminReportService;

    String adminToken;

    @BeforeEach
    void setUp() {
        adminToken = tokenProvider.issueAccessToken(9L, Role.ADMIN);
    }

    private Author author(long id) {
        return new Author(id, "닉네임", null);
    }

    private AdminReportSummary summary() {
        return new AdminReportSummary(1L, ReportTargetType.POST, 10L, author(2L),
                ReportReason.ABUSE, null, ReportStatus.PENDING, author(1L), LocalDateTime.now());
    }

    private AdminReportDetail detail() {
        return new AdminReportDetail(1L, ReportTargetType.POST, 10L, author(2L),
                ReportReason.ABUSE, null, ReportStatus.PENDING, author(1L), LocalDateTime.now(),
                "원본 내용", false, null, null, null);
    }

    @Test
    @DisplayName("일반 회원 토큰이면 403 ADMIN_ONLY — hasRole(ADMIN)이 실제로 막는다")
    void 목록_일반회원토큰_거절() throws Exception {
        given(userRepository.findById(any())).willReturn(Optional.of(User.builder().nickname("여행자").build()));
        String userToken = tokenProvider.issueAccessToken(1L, Role.USER);

        mvc.perform(get("/admin/reports").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADMIN_ONLY"));
    }

    @Test
    @DisplayName("status로 서비스에 위임한다")
    void 목록_정상() throws Exception {
        given(adminReportService.listReports(eq(ReportStatus.PENDING), any()))
                .willReturn(new PageImpl<>(List.of(summary())));

        mvc.perform(get("/admin/reports?status=PENDING").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.meta.page").value(1));
    }

    @Test
    @DisplayName("없는 신고면 404 REPORT_NOT_FOUND")
    void 상세_없음() throws Exception {
        given(adminReportService.getReportDetail(999L)).willThrow(new BusinessException(ErrorCode.REPORT_NOT_FOUND));

        mvc.perform(get("/admin/reports/999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REPORT_NOT_FOUND"));
    }

    @Test
    @DisplayName("정상 상세는 삭제 여부·실제 내용을 함께 돌려준다")
    void 상세_정상() throws Exception {
        given(adminReportService.getReportDetail(1L)).willReturn(detail());

        mvc.perform(get("/admin/reports/1").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetContent").value("원본 내용"))
                .andExpect(jsonPath("$.data.targetDeleted").value(false));
    }

    @Test
    @DisplayName("status 값이 이상하면 400 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 판정_잘못된값() throws Exception {
        mvc.perform(patch("/admin/reports/1")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"NOT_A_STATUS"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("서비스가 던진 REPORT_ALREADY_REVIEWED가 그대로 전달된다")
    void 판정_이미판정됨_전달() throws Exception {
        given(adminReportService.reviewReport(9L, 1L, ReportStatus.VALID, null))
                .willThrow(new BusinessException(ErrorCode.REPORT_ALREADY_REVIEWED));

        mvc.perform(patch("/admin/reports/1")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"VALID"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REPORT_ALREADY_REVIEWED"));
    }

    @Test
    @DisplayName("정상 판정은 로그인한 관리자 ID로 위임하고 결과를 그대로 돌려준다")
    void 판정_정상() throws Exception {
        given(adminReportService.reviewReport(9L, 1L, ReportStatus.VALID, "확인함"))
                .willReturn(new ReportReviewResult(detail(), 1, null));

        mvc.perform(patch("/admin/reports/1")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"VALID","note":"확인함"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetUserValidReportCount").value(1))
                .andExpect(jsonPath("$.data.appliedSanction").isEmpty());
    }
}
