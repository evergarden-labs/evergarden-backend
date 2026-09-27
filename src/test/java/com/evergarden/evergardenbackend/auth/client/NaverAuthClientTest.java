package com.evergarden.evergardenbackend.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.evergarden.evergardenbackend.auth.config.NaverOAuthProperties;
import com.evergarden.evergardenbackend.auth.entity.SocialProvider;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 네이버 토큰 검증(ADR-062) — 실제 HTTP 호출은 목 서버로 대체한다.
 *
 * <p>검증 전용 엔드포인트가 없어 프로필 조회 성공 여부(resultcode)로 대신한다.
 */
class NaverAuthClientTest {

    private static final String USER_INFO_URI = "https://openapi.naver.com/v1/nid/me";

    private MockRestServiceServer mockServer;
    private NaverAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new NaverAuthClient(builder.build(), new NaverOAuthProperties(USER_INFO_URI));
    }

    @Test
    @DisplayName("provider()는 NAVER")
    void provider() {
        assertThat(client.provider()).isEqualTo(SocialProvider.NAVER);
    }

    @Test
    @DisplayName("resultcode가 00이면 response.id를 돌려준다")
    void 검증_성공() {
        mockServer.expect(requestTo(USER_INFO_URI))
                .andExpect(header("Authorization", "Bearer token-abc"))
                .andRespond(withSuccess("""
                        {"resultcode":"00","message":"success","response":{"id":"32742776"}}
                        """, MediaType.APPLICATION_JSON));

        String providerUserId = client.verify("token-abc");

        assertThat(providerUserId).isEqualTo("32742776");
    }

    @Test
    @DisplayName("resultcode가 00이 아니면 SOCIAL_AUTH_FAILED")
    void resultcode_실패() {
        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("""
                        {"resultcode":"024","message":"Authentication failed"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.verify("bad-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SOCIAL_AUTH_FAILED);
    }

    @Test
    @DisplayName("네이버가 요청 자체를 거부하면 SOCIAL_AUTH_FAILED")
    void 제공자_거부() {
        mockServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.verify("bad-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SOCIAL_AUTH_FAILED);
    }
}
