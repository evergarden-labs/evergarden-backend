package com.evergarden.evergardenbackend.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.evergarden.evergardenbackend.user.dto.MyProfile;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import com.evergarden.evergardenbackend.user.service.OnboardingService;
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

/** `@Valid` 검증·인증 필요·서비스 위임을 확인한다. */
@ActiveProfiles("test")
@WebMvcTest(controllers = OnboardingController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class OnboardingControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean OnboardingService onboardingService;

    String accessToken;

    @BeforeEach
    void setUp() {
        User activeUser = User.builder().nickname("여행자").build();
        given(userRepository.findById(any())).willReturn(Optional.of(activeUser));
        accessToken = tokenProvider.issueAccessToken(1L, Role.USER);
    }

    // ── 초기 설정 완료 ────────────────────────────────────────

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 완료_비로그인() throws Exception {
        mvc.perform(patch("/users/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"새닉네임"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("닉네임 형식이 틀리면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 완료_닉네임형식오류() throws Exception {
        mvc.perform(patch("/users/me/onboarding")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"공백 있는 닉네임"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("정상 요청은 로그인한 사용자 ID로 서비스에 위임한다")
    void 완료_정상() throws Exception {
        given(onboardingService.completeOnboarding(eq(1L), any()))
                .willReturn(MyProfile.withoutStats(1L, "새닉네임", null, true));

        mvc.perform(patch("/users/me/onboarding")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"새닉네임"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));
    }

    @Test
    @DisplayName("서비스가 던진 ONBOARDING_ALREADY_COMPLETED가 그대로 전달된다")
    void 완료_이미완료_전달() throws Exception {
        given(onboardingService.completeOnboarding(eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.ONBOARDING_ALREADY_COMPLETED));

        mvc.perform(patch("/users/me/onboarding")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"새닉네임"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ONBOARDING_ALREADY_COMPLETED"));
    }

    // ── 초기 설정 건너뛰기 ────────────────────────────────────

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 건너뛰기_비로그인() throws Exception {
        mvc.perform(post("/users/me/onboarding/skip"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인한 사용자 ID로 서비스에 위임한다")
    void 건너뛰기_정상() throws Exception {
        given(onboardingService.skipOnboarding(1L))
                .willReturn(MyProfile.withoutStats(1L, "여행자1234", null, true));

        mvc.perform(post("/users/me/onboarding/skip").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(true));

        verify(onboardingService).skipOnboarding(1L);
    }
}
