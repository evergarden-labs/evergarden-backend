package com.evergarden.evergardenbackend.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.evergarden.evergardenbackend.user.dto.MyStats;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import com.evergarden.evergardenbackend.user.service.MyPageService;
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
@WebMvcTest(controllers = MyPageController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class MyPageControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean MyPageService myPageService;

    String accessToken;

    @BeforeEach
    void setUp() {
        User activeUser = User.builder().nickname("여행자").build();
        given(userRepository.findById(any())).willReturn(Optional.of(activeUser));
        accessToken = tokenProvider.issueAccessToken(1L, Role.USER);
    }

    private MyStats sampleStats() {
        return new MyStats(1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    // ── 프로필 조회 ──────────────────────────────────────────

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 조회_비로그인() throws Exception {
        mvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인한 사용자 ID로 서비스에 위임하고 stats까지 내려준다")
    void 조회_정상() throws Exception {
        given(myPageService.getMyProfile(1L))
                .willReturn(new MyProfile(1L, "여행자1234", null, true, sampleStats()));

        mvc.perform(get("/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("여행자1234"))
                .andExpect(jsonPath("$.data.stats.archiveCount").value(1))
                .andExpect(jsonPath("$.data.stats.unlockableCapsuleCount").value(9));
    }

    // ── 프로필 수정 ──────────────────────────────────────────

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 수정_비로그인() throws Exception {
        mvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"새닉네임"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("닉네임 형식이 틀리면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 수정_닉네임형식오류() throws Exception {
        mvc.perform(patch("/users/me")
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
    void 수정_정상() throws Exception {
        given(myPageService.updateMyProfile(eq(1L), any()))
                .willReturn(MyProfile.withoutStats(1L, "새닉네임", null, true));

        mvc.perform(patch("/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"새닉네임"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
                .andExpect(jsonPath("$.data.stats").doesNotExist());

        verify(myPageService).updateMyProfile(eq(1L), any());
    }

    @Test
    @DisplayName("서비스가 던진 NICKNAME_DUPLICATED가 그대로 전달된다")
    void 수정_닉네임중복_전달() throws Exception {
        given(myPageService.updateMyProfile(eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.NICKNAME_DUPLICATED));

        mvc.perform(patch("/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"인기닉네임"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NICKNAME_DUPLICATED"));
    }
}
