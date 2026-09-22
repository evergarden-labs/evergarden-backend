package com.evergarden.evergardenbackend.timecapsule.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.evergarden.evergardenbackend.global.response.CursorMeta;
import com.evergarden.evergardenbackend.global.response.CursorPage;
import com.evergarden.evergardenbackend.timecapsule.dto.TimeCapsuleDetail;
import com.evergarden.evergardenbackend.timecapsule.dto.TimeCapsuleSummary;
import com.evergarden.evergardenbackend.timecapsule.service.TimeCapsuleService;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.List;
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
@WebMvcTest(controllers = TimeCapsuleController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class TimeCapsuleControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean TimeCapsuleService timeCapsuleService;

    String accessToken;

    @BeforeEach
    void setUp() {
        User activeUser = User.builder().nickname("여행자").build();
        given(userRepository.findById(any())).willReturn(Optional.of(activeUser));
        accessToken = tokenProvider.issueAccessToken(1L, Role.USER);
    }

    @Test
    @DisplayName("title이 비어 있으면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 빈_제목() throws Exception {
        mvc.perform(post("/time-capsules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","content":"내용","unlockType":"DATE","unlockDate":"2027-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("해제 조건이 안 맞으면 서비스의 400이 그대로 전달된다")
    void 해제조건_오류() throws Exception {
        given(timeCapsuleService.create(eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_UNLOCK_CONDITION));

        mvc.perform(post("/time-capsules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제목","content":"내용","unlockType":"DATE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_UNLOCK_CONDITION"));
    }

    @Test
    @DisplayName("정상 요청은 로그인한 사용자 ID로 서비스에 위임한다")
    void 정상_생성() throws Exception {
        given(timeCapsuleService.create(eq(1L), any())).willReturn(mock(TimeCapsuleDetail.class));

        mvc.perform(post("/time-capsules")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제주 여행 기억","content":"그날의 기억","unlockType":"DATE","unlockDate":"2027-01-01"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 비로그인() throws Exception {
        mvc.perform(post("/time-capsules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제목","content":"내용","unlockType":"DATE","unlockDate":"2027-01-01"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ── 목록(TC-02·06) ───────────────────────────────────────

    @Test
    @DisplayName("목록은 로그인한 사용자 ID로 서비스에 위임하고, 커서 메타를 함께 돌려준다")
    void 목록_정상() throws Exception {
        given(timeCapsuleService.list(eq(1L), eq((String) null), eq(20)))
                .willReturn(new CursorPage<>(List.of(mock(TimeCapsuleSummary.class)), CursorMeta.last()));

        mvc.perform(get("/time-capsules").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.hasNext").value(false));
    }

    @Test
    @DisplayName("열어본 목록도 로그인한 사용자 ID로 서비스에 위임한다")
    void 열어본목록_정상() throws Exception {
        given(timeCapsuleService.listOpened(eq(1L), eq((String) null), eq(20)))
                .willReturn(new CursorPage<>(List.of(), CursorMeta.last()));

        mvc.perform(get("/time-capsules/opened").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("size가 범위를 벗어나면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 목록_size범위초과() throws Exception {
        mvc.perform(get("/time-capsules").header("Authorization", "Bearer " + accessToken)
                        .param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    // ── 조회(TC-03·06) ───────────────────────────────────────

    @Test
    @DisplayName("없는 캡슐을 조회하면 서비스의 404가 그대로 전달된다")
    void 조회_없는캡슐() throws Exception {
        given(timeCapsuleService.get(eq(1L), eq(99L)))
                .willThrow(new BusinessException(ErrorCode.TIME_CAPSULE_NOT_FOUND));

        mvc.perform(get("/time-capsules/99").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TIME_CAPSULE_NOT_FOUND"));
    }

    @Test
    @DisplayName("남의 캡슐을 조회하면 서비스의 403이 그대로 전달된다")
    void 조회_남의캡슐() throws Exception {
        given(timeCapsuleService.get(eq(1L), eq(5L)))
                .willThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER));

        mvc.perform(get("/time-capsules/5").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_RESOURCE_OWNER"));
    }

    @Test
    @DisplayName("정상 조회는 로그인한 사용자 ID로 서비스에 위임한다")
    void 정상_조회() throws Exception {
        given(timeCapsuleService.get(eq(1L), eq(5L))).willReturn(mock(TimeCapsuleDetail.class));

        mvc.perform(get("/time-capsules/5").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    // ── 삭제(TC-07) ──────────────────────────────────────────

    @Test
    @DisplayName("없는 캡슐을 삭제하면 서비스의 404가 그대로 전달된다")
    void 삭제_없는캡슐() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.TIME_CAPSULE_NOT_FOUND))
                .when(timeCapsuleService).delete(1L, 99L);

        mvc.perform(delete("/time-capsules/99").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TIME_CAPSULE_NOT_FOUND"));
    }

    @Test
    @DisplayName("삭제는 빈 성공 봉투를 돌려준다")
    void 삭제_성공() throws Exception {
        mvc.perform(delete("/time-capsules/5").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
