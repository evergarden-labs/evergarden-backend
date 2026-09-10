package com.evergarden.evergardenbackend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.global.config.SecurityConfig;
import com.evergarden.evergardenbackend.global.response.ApiResponse;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시큐리티 필터 단계의 거절이 {@code GlobalExceptionHandler}와 <b>같은 봉투</b>로
 * 나가는지 확인한다. 필터는 {@code DispatcherServlet} 앞에 있어 핸들러를 거치지 않는다.
 */
@ActiveProfiles("test")
@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        // @WebMvcTest 슬라이스에는 JsonMapper가 없다. SecurityErrorResponder가 이걸로 봉투를 쓴다
        JacksonAutoConfiguration.class})
class SecurityConfigTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;

    @Value("${jwt.secret-key}") String secretKey;

    /** 보호 경로 하나, 관리자 경로 하나, 공개 경로 둘. */
    @RestController
    static class ProbeController {
        @GetMapping("/me")
        ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal AuthPrincipal me) {
            return ApiResponse.of(Map.of("userId", me.userId(), "role", me.role().name()));
        }

        @GetMapping("/admin/reports")
        ApiResponse<String> adminOnly() {
            return ApiResponse.of("관리자 화면");
        }

        @PostMapping("/auth/social/google")
        ApiResponse<String> login() {
            return ApiResponse.of("로그인");
        }

        @PostMapping("/auth/restore")
        ApiResponse<String> restore() {
            return ApiResponse.of("복구");
        }
    }

    User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder().nickname("여행자").build();
        given(userRepository.findById(any())).willReturn(Optional.of(activeUser));
    }

    /** 만료·위조 토큰은 provider가 만들어주지 않으므로 직접 만든다. */
    String tokenExpiredAt(Instant expiry) {
        return Jwts.builder().subject("1").claim("role", "USER").claim("type", "ACCESS")
                .issuedAt(Date.from(expiry.minus(30, ChronoUnit.MINUTES)))
                .expiration(Date.from(expiry))
                .signWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Nested
    @DisplayName("인증 실패 (401)")
    class Unauthenticated {

        @Test
        @DisplayName("토큰이 없으면 UNAUTHENTICATED — 봉투 모양이 오류 응답과 같다")
        void noToken() throws Exception {
            mvc.perform(get("/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.error.message").value("로그인이 필요합니다."))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("만료된 토큰은 TOKEN_EXPIRED — 클라이언트가 재발급을 시도할 수 있다")
        void expired() throws Exception {
            mvc.perform(get("/me").header("Authorization",
                            "Bearer " + tokenExpiredAt(Instant.now().minusSeconds(60))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
        }

        @Test
        @DisplayName("서명이 다른 토큰은 TOKEN_INVALID — 재발급 대상이 아니다")
        void forged() throws Exception {
            String forged = Jwts.builder().subject("1").claim("role", "ADMIN").claim("type", "ACCESS")
                    .expiration(Date.from(Instant.now().plusSeconds(600)))
                    .signWith(Keys.hmacShaKeyFor(
                            "attacker-key-attacker-key-attacker-key-0123".getBytes(StandardCharsets.UTF_8)))
                    .compact();

            mvc.perform(get("/me").header("Authorization", "Bearer " + forged))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
        }

        @Test
        @DisplayName("리프레시 토큰으로는 API를 부를 수 없다 — 7일짜리로 30분 만료를 우회하는 것을 막는다")
        void refreshTokenRejected() throws Exception {
            mvc.perform(get("/me").header("Authorization",
                            "Bearer " + tokenProvider.issueRefreshToken(1L, Role.USER)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
        }
    }

    @Nested
    @DisplayName("회원 상태 (403)")
    class Status {

        @Test
        @DisplayName("차단된 회원은 USER_BLOCKED — 토큰이 아직 살아 있어도 막힌다")
        void blocked() throws Exception {
            activeUser.block();

            mvc.perform(get("/me").header("Authorization",
                            "Bearer " + tokenProvider.issueAccessToken(1L, Role.USER)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("USER_BLOCKED"));
        }

        @Test
        @DisplayName("탈퇴한 회원은 USER_WITHDRAWN + 복구 기한(ADR-054)")
        void withdrawn() throws Exception {
            activeUser.withdraw(LocalDateTime.now().minusDays(3));

            mvc.perform(get("/me").header("Authorization",
                            "Bearer " + tokenProvider.issueAccessToken(1L, Role.USER)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("USER_WITHDRAWN"))
                    .andExpect(jsonPath("$.error.details.restorableUntil").exists());
        }

        @Test
        @DisplayName("탈퇴한 회원도 복구는 부를 수 있다 — 막으면 되돌릴 방법이 없다")
        void withdrawnCanStillRestore() throws Exception {
            activeUser.withdraw(LocalDateTime.now().minusDays(3));

            mvc.perform(post("/auth/restore").header("Authorization",
                            "Bearer " + tokenProvider.issueAccessToken(1L, Role.USER)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("권한 (ADR-036)")
    class Authorization {

        @Test
        @DisplayName("일반 회원이 관리자 API를 부르면 ADMIN_ONLY")
        void userCannotUseAdminApi() throws Exception {
            mvc.perform(get("/admin/reports").header("Authorization",
                            "Bearer " + tokenProvider.issueAccessToken(1L, Role.USER)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("ADMIN_ONLY"));
        }

        @Test
        @DisplayName("관리자 토큰이면 통과한다 — users를 조회하지 않는다(admins에 있으므로)")
        void adminPasses() throws Exception {
            mvc.perform(get("/admin/reports").header("Authorization",
                            "Bearer " + tokenProvider.issueAccessToken(7L, Role.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("관리자 화면"));
        }
    }

    @Nested
    @DisplayName("정상 흐름")
    class Allowed {

        @Test
        @DisplayName("유효한 토큰이면 컨트롤러가 주체를 받는다")
        void authenticated() throws Exception {
            mvc.perform(get("/me").header("Authorization",
                            "Bearer " + tokenProvider.issueAccessToken(42L, Role.USER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(42))
                    .andExpect(jsonPath("$.data.role").value("USER"));
        }

        @Test
        @DisplayName("명세에서 security: [] 인 경로는 토큰 없이 통과한다")
        void publicOperation() throws Exception {
            mvc.perform(post("/auth/social/google"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("발급한 토큰을 다시 파싱하면 같은 값이 나온다")
        void roundTrip() {
            AuthPrincipal parsed =
                    tokenProvider.parseAccessToken(tokenProvider.issueAccessToken(9L, Role.ADMIN));

            assertThat(parsed).isEqualTo(new AuthPrincipal(9L, Role.ADMIN));
            assertThat(parsed.isAdmin()).isTrue();
        }
    }
}
