package com.evergarden.evergardenbackend.trip.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.evergarden.evergardenbackend.trip.dto.TripDetail;
import com.evergarden.evergardenbackend.trip.service.TripImportService;
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

/**
 * {@code @Valid}(title 길이) 검증과, 서비스 예외가 오류 응답으로 그대로 전달되는지 확인한다 —
 * 같은 이유로 {@code TripControllerTest}와 동일하게 {@code @WebMvcTest}를 쓴다.
 */
@ActiveProfiles("test")
@WebMvcTest(controllers = TripImportController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class TripImportControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean TripImportService tripImportService;

    String accessToken;

    @BeforeEach
    void setUp() {
        User activeUser = User.builder().nickname("여행자").build();
        given(userRepository.findById(any())).willReturn(Optional.of(activeUser));
        accessToken = tokenProvider.issueAccessToken(1L, Role.USER);
    }

    @Test
    @DisplayName("title이 60자를 넘으면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 제목_길이초과() throws Exception {
        String longTitle = "가".repeat(61);
        mvc.perform(post("/posts/5/course/import")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"2026-05-01","title":"%s"}
                                """.formatted(longTitle)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("정상 요청은 로그인한 사용자 ID로 서비스에 위임한다")
    void 정상_가져오기() throws Exception {
        given(tripImportService.importCourse(eq(1L), eq(5L), any())).willReturn(mock(TripDetail.class));

        mvc.perform(post("/posts/5/course/import")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"2026-05-01"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("본문 없이 호출하면 INVALID_REQUEST — 서비스를 부르지 않는다(ADR-059)")
    void 본문없이_호출() throws Exception {
        mvc.perform(post("/posts/5/course/import").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verify(tripImportService, never()).importCourse(any(), any(), any());
    }

    @Test
    @DisplayName("startDate가 없으면 INVALID_REQUEST — 서비스를 부르지 않는다(ADR-059)")
    void startDate_생략() throws Exception {
        mvc.perform(post("/posts/5/course/import")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verify(tripImportService, never()).importCourse(any(), any(), any());
    }

    @Test
    @DisplayName("없는 게시물은 404 POST_NOT_FOUND")
    void 없는_게시물() throws Exception {
        given(tripImportService.importCourse(eq(1L), eq(999L), any()))
                .willThrow(new BusinessException(ErrorCode.POST_NOT_FOUND));

        mvc.perform(post("/posts/999/course/import")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"2026-05-01"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("공유된 코스가 없으면 404 TRIP_NOT_FOUND")
    void 공유코스_없음() throws Exception {
        given(tripImportService.importCourse(eq(1L), eq(5L), any()))
                .willThrow(new BusinessException(ErrorCode.TRIP_NOT_FOUND));

        mvc.perform(post("/posts/5/course/import")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"2026-05-01"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
    }
}
