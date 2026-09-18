package com.evergarden.evergardenbackend.place.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
import com.evergarden.evergardenbackend.place.dto.PlaceDetail;
import com.evergarden.evergardenbackend.place.service.PlaceQueryService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * `@Size`(keyword) 검증과, 서비스 예외가 오류 응답으로 그대로 전달되는지 확인한다 — 같은
 * 이유로 {@code TripControllerTest}와 동일하게 {@code @WebMvcTest}를 쓴다.
 */
@ActiveProfiles("test")
@WebMvcTest(controllers = PlaceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class PlaceControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean PlaceQueryService placeQueryService;

    String accessToken;

    @BeforeEach
    void setUp() {
        User activeUser = User.builder().nickname("여행자").build();
        given(userRepository.findById(any())).willReturn(Optional.of(activeUser));
        accessToken = tokenProvider.issueAccessToken(1L, Role.USER);
    }

    // ── 검색 ─────────────────────────────────────────────

    @Test
    @DisplayName("keyword와 regionCode가 둘 다 없어 INVALID_REQUEST를 던지면 그대로 전달된다")
    void 검색_조건없음_전달() throws Exception {
        given(placeQueryService.search(eq(null), eq(null), eq(null), any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_REQUEST));

        mvc.perform(get("/places/search").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("keyword가 50자를 넘으면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 검색_키워드_길이초과() throws Exception {
        String longKeyword = "가".repeat(51);
        mvc.perform(get("/places/search").param("keyword", longKeyword)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("존재하지 않는 regionCode는 404 REGION_NOT_FOUND")
    void 검색_없는지역_전달() throws Exception {
        given(placeQueryService.search(eq(null), eq("999"), eq(null), any()))
                .willThrow(new BusinessException(ErrorCode.REGION_NOT_FOUND));

        mvc.perform(get("/places/search").param("regionCode", "999")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REGION_NOT_FOUND"));
    }

    @Test
    @DisplayName("정상 검색은 목록과 페이지 메타를 돌려준다")
    void 검색_정상() throws Exception {
        given(placeQueryService.search(eq("남산"), eq(null), eq(null), any()))
                .willReturn(new PageImpl<>(java.util.List.of()));

        mvc.perform(get("/places/search").param("keyword", "남산")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ── 상세 조회 ─────────────────────────────────────────

    @Test
    @DisplayName("없는 장소는 404 PLACE_NOT_FOUND")
    void 상세_없는장소() throws Exception {
        given(placeQueryService.getPlace(999L)).willThrow(new BusinessException(ErrorCode.PLACE_NOT_FOUND));

        mvc.perform(get("/places/999").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    @DisplayName("정상 조회는 200을 돌려준다")
    void 상세_정상() throws Exception {
        given(placeQueryService.getPlace(1L)).willReturn(org.mockito.Mockito.mock(PlaceDetail.class));

        mvc.perform(get("/places/1").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }
}
