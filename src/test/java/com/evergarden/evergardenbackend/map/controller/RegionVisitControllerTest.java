package com.evergarden.evergardenbackend.map.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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
import com.evergarden.evergardenbackend.map.dto.RegionVisitResult;
import com.evergarden.evergardenbackend.map.dto.VisitReward;
import com.evergarden.evergardenbackend.map.service.RegionVisitService;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import org.springframework.http.MediaType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 인증·기본값·서비스 위임을 확인한다 — 다른 컨트롤러 테스트와 같은 이유로 {@code @WebMvcTest}를 쓴다. */
@ActiveProfiles("test")
@WebMvcTest(controllers = RegionVisitController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class RegionVisitControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean RegionVisitService regionVisitService;

    String accessToken;

    @BeforeEach
    void setUp() {
        User activeUser = User.builder().nickname("여행자").build();
        given(userRepository.findById(any())).willReturn(Optional.of(activeUser));
        accessToken = tokenProvider.issueAccessToken(1L, Role.USER);
    }

    @Test
    @DisplayName("level을 생략하면 SIDO로 서비스에 위임한다")
    void level_기본값_SIDO() throws Exception {
        given(regionVisitService.listMyRegions(eq(1L), eq(RegionLevel.SIDO))).willReturn(List.of());

        mvc.perform(get("/users/me/regions").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("level=SIGUNGU를 보내면 그대로 서비스에 위임한다")
    void level_SIGUNGU() throws Exception {
        given(regionVisitService.listMyRegions(eq(1L), eq(RegionLevel.SIGUNGU))).willReturn(List.of());

        mvc.perform(get("/users/me/regions?level=SIGUNGU").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 비로그인() throws Exception {
        mvc.perform(get("/users/me/regions"))
                .andExpect(status().isUnauthorized());
    }

    // ── 지역 인증(MAP-01) ──────────────────────────────────────

    @Test
    @DisplayName("좌표가 없으면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 인증_좌표없음() throws Exception {
        mvc.perform(post("/region-visits")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat":37.5,"accuracyMeters":10}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("정확도 초과면 서비스의 400이 그대로 전달된다")
    void 인증_정확도초과_전달() throws Exception {
        given(regionVisitService.verify(eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.LOCATION_ACCURACY_TOO_LOW));

        mvc.perform(post("/region-visits")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat":37.5,"lng":127.0,"accuracyMeters":150}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("LOCATION_ACCURACY_TOO_LOW"));
    }

    @Test
    @DisplayName("정상 요청은 로그인한 사용자 ID로 서비스에 위임한다")
    void 인증_정상() throws Exception {
        given(regionVisitService.verify(eq(1L), any())).willReturn(mock(RegionVisitResult.class));

        mvc.perform(post("/region-visits")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat":37.5,"lng":127.0,"accuracyMeters":10}
                                """))
                .andExpect(status().isOk());
    }

    // ── 보상 다시 보기(GARDEN-02) ────────────────────────────

    @Test
    @DisplayName("없는 방문이면 서비스의 404가 그대로 전달된다")
    void 보상조회_없는방문() throws Exception {
        given(regionVisitService.getVisitReward(eq(1L), eq(99L)))
                .willThrow(new BusinessException(ErrorCode.REGION_VISIT_NOT_FOUND));

        mvc.perform(get("/region-visits/99/reward").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REGION_VISIT_NOT_FOUND"));
    }

    @Test
    @DisplayName("남의 방문이면 서비스의 403이 그대로 전달된다")
    void 보상조회_남의방문() throws Exception {
        given(regionVisitService.getVisitReward(eq(1L), eq(5L)))
                .willThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER));

        mvc.perform(get("/region-visits/5/reward").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_RESOURCE_OWNER"));
    }

    @Test
    @DisplayName("정상 조회는 로그인한 사용자 ID로 서비스에 위임한다")
    void 보상조회_정상() throws Exception {
        given(regionVisitService.getVisitReward(eq(1L), eq(5L))).willReturn(mock(VisitReward.class));

        mvc.perform(get("/region-visits/5/reward").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }
}
