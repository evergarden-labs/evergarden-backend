package com.evergarden.evergardenbackend.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.evergarden.evergardenbackend.report.dto.ReportResult;
import com.evergarden.evergardenbackend.report.entity.ReportStatus;
import com.evergarden.evergardenbackend.report.service.ReportService;
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
@WebMvcTest(controllers = PostReportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class PostReportControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean ReportService reportService;

    String accessToken;

    @BeforeEach
    void setUp() {
        User activeUser = User.builder().nickname("여행자").build();
        given(userRepository.findById(any())).willReturn(Optional.of(activeUser));
        accessToken = tokenProvider.issueAccessToken(1L, Role.USER);
    }

    @Test
    @DisplayName("사유가 없으면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 사유없음() throws Exception {
        mvc.perform(post("/posts/5/reports")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("중복 신고는 서비스의 409가 그대로 전달된다")
    void 중복신고() throws Exception {
        given(reportService.reportPost(eq(1L), eq(5L), any()))
                .willThrow(new BusinessException(ErrorCode.ALREADY_REPORTED));

        mvc.perform(post("/posts/5/reports")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"SPAM"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_REPORTED"));
    }

    @Test
    @DisplayName("정상 신고는 로그인한 사용자 ID로 서비스에 위임한다")
    void 정상_신고() throws Exception {
        given(reportService.reportPost(eq(1L), eq(5L), any())).willReturn(new ReportResult(1L, ReportStatus.PENDING));

        mvc.perform(post("/posts/5/reports")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"SPAM"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }
}
