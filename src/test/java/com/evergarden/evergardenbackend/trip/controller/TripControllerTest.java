package com.evergarden.evergardenbackend.trip.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.evergarden.evergardenbackend.place.dto.PlaceSummary;
import com.evergarden.evergardenbackend.trip.dto.AutoArrangeResult;
import com.evergarden.evergardenbackend.trip.dto.TripDetail;
import com.evergarden.evergardenbackend.trip.dto.TripRoute;
import com.evergarden.evergardenbackend.trip.service.TripAutoArrangeService;
import com.evergarden.evergardenbackend.trip.service.TripNearbyPlaceService;
import com.evergarden.evergardenbackend.trip.service.TripRouteService;
import com.evergarden.evergardenbackend.trip.service.TripService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * `@Valid`·`@Min`/`@Max` 검증이 실제로 걸리는지 확인한다 — 아카이브 컨트롤러 테스트와
 * 같은 이유로 {@code standaloneSetup}이 아니라 {@code @WebMvcTest}를 쓴다.
 */
@ActiveProfiles("test")
@WebMvcTest(controllers = TripController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class TripControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean TripService tripService;
    @MockitoBean TripRouteService tripRouteService;
    @MockitoBean TripAutoArrangeService tripAutoArrangeService;
    @MockitoBean TripNearbyPlaceService tripNearbyPlaceService;

    String accessToken;

    @BeforeEach
    void setUp() {
        User activeUser = User.builder().nickname("여행자").build();
        given(userRepository.findById(any())).willReturn(Optional.of(activeUser));
        accessToken = tokenProvider.issueAccessToken(1L, Role.USER);
    }

    // ── 목록: @Min/@Max ──────────────────────────────────────────

    @Test
    @DisplayName("page가 0이면 INVALID_REQUEST")
    void page_0이면_거절() throws Exception {
        mvc.perform(get("/trips?page=0").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("size가 50을 넘으면 INVALID_REQUEST")
    void size_초과면_거절() throws Exception {
        mvc.perform(get("/trips?size=51").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("기본값으로 정상 조회된다")
    void 기본값_조회() throws Exception {
        given(tripService.list(eq(1L), any())).willReturn(new PageImpl<>(java.util.List.of()));

        mvc.perform(get("/trips").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ── 생성: @Valid ─────────────────────────────────────────────

    @Test
    @DisplayName("title이 비어 있으면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 빈_제목() throws Exception {
        mvc.perform(post("/trips")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","startDate":"2026-01-01","endDate":"2026-01-03","regionCodes":["50"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("regionCodes가 비어 있으면 INVALID_REQUEST")
    void 여행지_누락() throws Exception {
        mvc.perform(post("/trips")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제주","startDate":"2026-01-01","endDate":"2026-01-03","regionCodes":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("정상 요청은 로그인한 사용자 ID로 서비스에 위임한다")
    void 정상_생성() throws Exception {
        given(tripService.create(eq(1L), any())).willReturn(mock(TripDetail.class));

        mvc.perform(post("/trips")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제주도 여행","startDate":"2026-01-01","endDate":"2026-01-03","regionCodes":["50"]}
                                """))
                .andExpect(status().isOk());
    }

    // ── 조회·수정·삭제: 서비스 예외가 그대로 전달되는지 ──────────

    @Test
    @DisplayName("없는 일정 조회는 404 TRIP_NOT_FOUND")
    void 없는_일정() throws Exception {
        given(tripService.get(eq(1L), eq(999L)))
                .willThrow(new BusinessException(ErrorCode.TRIP_NOT_FOUND));

        mvc.perform(get("/trips/999").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
    }

    @Test
    @DisplayName("소유자가 아니면 수정 시 403 NOT_RESOURCE_OWNER")
    void 소유자가_아닌_수정() throws Exception {
        given(tripService.update(eq(1L), eq(5L), any()))
                .willThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER));

        mvc.perform(patch("/trips/5")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"새 이름"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_RESOURCE_OWNER"));
    }

    @Test
    @DisplayName("빈 수정 본문은 INVALID_REQUEST가 그대로 전달된다")
    void 빈_수정본문() throws Exception {
        given(tripService.update(eq(1L), eq(5L), any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_REQUEST));

        mvc.perform(patch("/trips/5")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("삭제는 빈 성공 봉투를 돌려준다")
    void 삭제_성공() throws Exception {
        mvc.perform(delete("/trips/5").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ── 동선 조회 ─────────────────────────────────────────

    @Test
    @DisplayName("동선 조회는 로그인한 사용자 ID로 서비스에 위임한다")
    void 동선_조회() throws Exception {
        given(tripRouteService.getRoute(eq(1L), eq(5L))).willReturn(mock(TripRoute.class));

        mvc.perform(get("/trips/5/route").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("없는 일정의 동선 조회는 404 TRIP_NOT_FOUND")
    void 동선_없는일정() throws Exception {
        given(tripRouteService.getRoute(eq(1L), eq(999L)))
                .willThrow(new BusinessException(ErrorCode.TRIP_NOT_FOUND));

        mvc.perform(get("/trips/999/route").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
    }

    // ── 자동 배치 ─────────────────────────────────────────

    @Test
    @DisplayName("본문 없이 호출해도 로그인한 사용자 ID로 서비스에 위임한다")
    void 자동배치_본문없음() throws Exception {
        given(tripAutoArrangeService.propose(eq(1L), eq(5L), eq(null))).willReturn(mock(AutoArrangeResult.class));

        mvc.perform(post("/trips/5/auto-arrange").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("배치할 장소가 없으면 400 INVALID_REQUEST가 그대로 전달된다")
    void 자동배치_장소없음() throws Exception {
        given(tripAutoArrangeService.propose(eq(1L), eq(5L), any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_REQUEST));

        mvc.perform(post("/trips/5/auto-arrange")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── 자동 배치 적용(ADR-060) ────────────────────────────

    @Test
    @DisplayName("빈 items는 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 자동배치적용_빈목록() throws Exception {
        mvc.perform(post("/trips/5/auto-arrange/apply")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("tripPlaceId가 있는 항목과 없는 항목이 섞인 요청을 그대로 위임한다")
    void 자동배치적용_정상() throws Exception {
        given(tripAutoArrangeService.apply(eq(1L), eq(5L), any())).willReturn(mock(TripDetail.class));

        mvc.perform(post("/trips/5/auto-arrange/apply")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[
                                    {"tripPlaceId":101,"dayNumber":1,"sortOrder":1,
                                     "place":{"placeId":1,"title":"남산","lat":37.5,"lng":127.0,"region":{"code":"11","name":"서울","level":"SIDO"}}},
                                    {"tripPlaceId":null,"dayNumber":1,"sortOrder":2,
                                     "place":{"placeId":2,"title":"덕수궁","lat":37.6,"lng":127.0,"region":{"code":"11","name":"서울","level":"SIDO"}}}
                                ]}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("없는 일정이면 404 TRIP_NOT_FOUND")
    void 자동배치적용_없는일정() throws Exception {
        given(tripAutoArrangeService.apply(eq(1L), eq(999L), any()))
                .willThrow(new BusinessException(ErrorCode.TRIP_NOT_FOUND));

        mvc.perform(post("/trips/999/auto-arrange/apply")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"tripPlaceId":1,"dayNumber":1,"sortOrder":1,
                                    "place":{"placeId":1,"title":"남산","lat":37.5,"lng":127.0,"region":{"code":"11","name":"서울","level":"SIDO"}}}]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
    }

    // ── 주변 추천 장소 ────────────────────────────────────

    @Test
    @DisplayName("dayNumber 없이 호출해도 로그인한 사용자 ID로 서비스에 위임한다")
    void 주변장소_dayNumber없이() throws Exception {
        given(tripNearbyPlaceService.listNearby(eq(1L), eq(5L), eq(null), any()))
                .willReturn(new PageImpl<>(java.util.List.<PlaceSummary>of()));

        mvc.perform(get("/trips/5/nearby-places").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("dayNumber가 0이면 INVALID_REQUEST")
    void 주변장소_dayNumber_0() throws Exception {
        mvc.perform(get("/trips/5/nearby-places?dayNumber=0").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("소유자가 아니면 403 NOT_RESOURCE_OWNER가 그대로 전달된다")
    void 주변장소_소유자아님() throws Exception {
        given(tripNearbyPlaceService.listNearby(eq(1L), eq(5L), any(), any()))
                .willThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER));

        mvc.perform(get("/trips/5/nearby-places").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_RESOURCE_OWNER"));
    }
}
