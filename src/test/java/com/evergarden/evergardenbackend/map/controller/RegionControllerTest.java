package com.evergarden.evergardenbackend.map.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.evergarden.evergardenbackend.map.dto.RegionDetail;
import com.evergarden.evergardenbackend.map.service.RegionService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** `@WebMvcTest`로 서비스 예외 전달과 서비스 위임을 확인한다 — 다른 컨트롤러 테스트와 같은 이유. */
@ActiveProfiles("test")
@WebMvcTest(controllers = RegionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class RegionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean RegionService regionService;

    String accessToken;

    @BeforeEach
    void setUp() {
        User activeUser = User.builder().nickname("여행자").build();
        given(userRepository.findById(any())).willReturn(Optional.of(activeUser));
        accessToken = tokenProvider.issueAccessToken(1L, Role.USER);
    }

    // ── 지역 상세 ─────────────────────────────────────────

    @Test
    @DisplayName("없는 지역이면 서비스의 404가 그대로 전달된다")
    void 상세_없는지역() throws Exception {
        given(regionService.getRegion(eq(1L), eq("99")))
                .willThrow(new BusinessException(ErrorCode.REGION_NOT_FOUND));

        mvc.perform(get("/regions/99").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REGION_NOT_FOUND"));
    }

    @Test
    @DisplayName("정상 조회는 로그인한 사용자 ID로 서비스에 위임한다")
    void 상세_정상() throws Exception {
        given(regionService.getRegion(eq(1L), eq("11"))).willReturn(mock(RegionDetail.class));

        mvc.perform(get("/regions/11").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 상세_비로그인() throws Exception {
        mvc.perform(get("/regions/11"))
                .andExpect(status().isUnauthorized());
    }

    // ── 지역별 관광정보 ────────────────────────────────────

    @Test
    @DisplayName("없는 지역이면 서비스의 404가 그대로 전달된다")
    void 관광정보_없는지역() throws Exception {
        given(regionService.listRegionPlaces(eq("99"), eq(null), any()))
                .willThrow(new BusinessException(ErrorCode.REGION_NOT_FOUND));

        mvc.perform(get("/regions/99/places").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REGION_NOT_FOUND"));
    }

    @Test
    @DisplayName("정상 조회는 목록과 페이지 메타를 돌려준다")
    void 관광정보_정상() throws Exception {
        given(regionService.listRegionPlaces(eq("11"), eq(null), any()))
                .willReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/regions/11/places").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
