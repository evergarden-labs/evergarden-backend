package com.evergarden.evergardenbackend.trip.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.evergarden.evergardenbackend.trip.dto.TripPlaceResponse;
import com.evergarden.evergardenbackend.trip.service.TripPlaceService;
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
 * {@code @Valid} 검증과, 서비스 예외가 오류 응답으로 그대로 전달되는지 확인한다 — 같은
 * 이유로 {@code TripControllerTest}와 동일하게 {@code @WebMvcTest}를 쓴다.
 */
@ActiveProfiles("test")
@WebMvcTest(controllers = TripPlaceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class TripPlaceControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean TripPlaceService tripPlaceService;

    String accessToken;

    @BeforeEach
    void setUp() {
        User activeUser = User.builder().nickname("여행자").build();
        given(userRepository.findById(any())).willReturn(Optional.of(activeUser));
        accessToken = tokenProvider.issueAccessToken(1L, Role.USER);
    }

    // ── 추가: @Valid ─────────────────────────────────────────────

    @Test
    @DisplayName("placeId가 없으면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 추가_placeId_누락() throws Exception {
        mvc.perform(post("/trips/10/places")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dayNumber":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("dayNumber가 1 미만이면 INVALID_REQUEST")
    void 추가_dayNumber_범위밖() throws Exception {
        mvc.perform(post("/trips/10/places")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placeId":1,"dayNumber":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("정상 요청은 로그인한 사용자 ID로 서비스에 위임한다")
    void 추가_정상() throws Exception {
        given(tripPlaceService.addPlace(eq(1L), eq(10L), any())).willReturn(mock(TripPlaceResponse.class));

        mvc.perform(post("/trips/10/places")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placeId":1,"dayNumber":1}
                                """))
                .andExpect(status().isOk());
    }

    // ── 수정 ─────────────────────────────────────────────

    @Test
    @DisplayName("없는 tripPlace 수정은 404 TRIP_PLACE_NOT_FOUND")
    void 수정_없는항목() throws Exception {
        given(tripPlaceService.updatePlace(eq(1L), eq(10L), eq(999L), any()))
                .willThrow(new BusinessException(ErrorCode.TRIP_PLACE_NOT_FOUND));

        mvc.perform(patch("/trips/10/places/999")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memo":"새 메모"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRIP_PLACE_NOT_FOUND"));
    }

    @Test
    @DisplayName("소유자가 아니면 403 NOT_RESOURCE_OWNER")
    void 수정_소유자아님() throws Exception {
        given(tripPlaceService.updatePlace(eq(1L), eq(10L), eq(101L), any()))
                .willThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER));

        mvc.perform(patch("/trips/10/places/101")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memo":"새 메모"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_RESOURCE_OWNER"));
    }

    // ── 제거 ─────────────────────────────────────────────

    @Test
    @DisplayName("제거는 빈 성공 봉투를 돌려준다")
    void 제거_성공() throws Exception {
        mvc.perform(delete("/trips/10/places/101").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ── 순서 재배열: @Valid ────────────────────────────────

    @Test
    @DisplayName("items가 비어 있으면 INVALID_REQUEST")
    void 재배열_빈목록() throws Exception {
        mvc.perform(put("/trips/10/places/order")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("정상 재배열 요청은 상세를 돌려준다")
    void 재배열_정상() throws Exception {
        given(tripPlaceService.replaceOrder(eq(1L), eq(10L), any())).willReturn(mock(TripDetail.class));

        mvc.perform(put("/trips/10/places/order")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"tripPlaceId":101,"dayNumber":1,"sortOrder":1}]}
                                """))
                .andExpect(status().isOk());
    }
}
