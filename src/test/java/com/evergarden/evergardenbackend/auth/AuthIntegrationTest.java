package com.evergarden.evergardenbackend.auth;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.auth.client.GoogleAuthClient;
import com.evergarden.evergardenbackend.auth.entity.SocialProvider;
import com.evergarden.evergardenbackend.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 PostgreSQL·Redis·스프링 컨텍스트로 로그인→인증된 API 호출→로그아웃→재발급 실패까지
 * 전체 흐름을 확인한다. {@link GoogleAuthClient}만 목으로 두고(실제 구글에 묻지 않는다,
 * {@code RegionVisitIntegrationTest}가 {@code TourApiClient}를 목으로 두는 것과 같은 이유)
 * 나머지는 전부 진짜 빈으로 엮는다 — {@code jti}·Redis 저장·rotation이 실제로 맞물리는지는
 * 여기서만 확인할 수 있다.
 *
 * <p>{@code @Transactional}이 DB는 롤백해도 Redis 키는 남는다 — 유저마다 새 ID(시퀀스는
 * 롤백돼도 되돌아가지 않는다)를 써서 테스트 간 충돌은 없다.
 */
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper jsonMapper;

    @MockitoBean GoogleAuthClient googleAuthClient;

    @BeforeEach
    void setUp() {
        given(googleAuthClient.provider()).willReturn(SocialProvider.GOOGLE);
    }

    @Test
    @DisplayName("로그인 → 로그아웃 → 그 리프레시 토큰으로 재발급 시도하면 실패한다")
    void 로그인_로그아웃_흐름() throws Exception {
        given(googleAuthClient.verify("sdk-token")).willReturn("google-uid-1");

        MvcResult loginResult = mvc.perform(post("/auth/social/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"socialAccessToken":"sdk-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false))
                .andReturn();

        JsonNode data = jsonMapper.readTree(loginResult.getResponse().getContentAsString()).path("data");
        String accessToken = data.path("accessToken").asText();
        String refreshToken = data.path("refreshToken").asText();

        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("재발급(rotation) 성공 후에는 예전 리프레시 토큰이 무효화된다")
    void 재발급_후_예전토큰_무효화() throws Exception {
        given(googleAuthClient.verify("sdk-token")).willReturn("google-uid-2");

        MvcResult loginResult = mvc.perform(post("/auth/social/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"socialAccessToken":"sdk-token"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String oldRefreshToken = jsonMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();

        mvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefreshToken + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("같은 소셜 계정으로 다시 로그인하면 기존 회원으로 인식하고, 이전 세션은 무효화된다")
    void 재로그인시_이전_세션_무효화() throws Exception {
        given(googleAuthClient.verify("sdk-token")).willReturn("google-uid-3");

        MvcResult first = mvc.perform(post("/auth/social/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"socialAccessToken":"sdk-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andReturn();
        String firstRefreshToken = jsonMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();

        mvc.perform(post("/auth/social/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"socialAccessToken":"sdk-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false));

        mvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("탈퇴 → 같은 소셜 계정으로 로그인하면 USER_WITHDRAWN → 복구하면 다시 로그인된다")
    void 탈퇴_후_복구_흐름() throws Exception {
        given(googleAuthClient.verify("sdk-token")).willReturn("google-uid-4");

        MvcResult loginResult = mvc.perform(post("/auth/social/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"socialAccessToken":"sdk-token"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = jsonMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        mvc.perform(delete("/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mvc.perform(post("/auth/social/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"socialAccessToken":"sdk-token"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("USER_WITHDRAWN"))
                .andExpect(jsonPath("$.error.details.restorableUntil").exists());

        mvc.perform(post("/auth/restore/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"socialAccessToken":"sdk-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false));

        mvc.perform(post("/auth/social/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"socialAccessToken":"sdk-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false));
    }
}
