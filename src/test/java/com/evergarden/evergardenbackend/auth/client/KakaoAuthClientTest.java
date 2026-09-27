package com.evergarden.evergardenbackend.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.evergarden.evergardenbackend.auth.config.KakaoOAuthProperties;
import com.evergarden.evergardenbackend.auth.entity.SocialProvider;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 카카오 토큰 검증(ADR-062) — 실제 HTTP 호출은 목 서버로 대체한다. */
class KakaoAuthClientTest {

    private static final String TOKEN_INFO_URI = "https://kapi.kakao.com/v1/user/access_token_info";
    private static final String APP_ID = "1234";

    private MockRestServiceServer mockServer;
    private KakaoAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoAuthClient(builder.build(), new KakaoOAuthProperties(TOKEN_INFO_URI, APP_ID));
    }

    @Test
    @DisplayName("provider()는 KAKAO")
    void provider() {
        assertThat(client.provider()).isEqualTo(SocialProvider.KAKAO);
    }

    @Test
    @DisplayName("app_id가 우리 app-id와 같으면 id를 문자열로 돌려준다")
    void 검증_성공() {
        mockServer.expect(requestTo(TOKEN_INFO_URI))
                .andExpect(header("Authorization", "Bearer token-abc"))
                .andRespond(withSuccess("""
                        {"id":123456789,"expires_in":7199,"app_id":1234}
                        """, MediaType.APPLICATION_JSON));

        String providerUserId = client.verify("token-abc");

        assertThat(providerUserId).isEqualTo("123456789");
    }

    @Test
    @DisplayName("app_id가 다르면 SOCIAL_AUTH_FAILED — 남의 앱용으로 발급된 토큰이다")
    void appId_불일치() {
        mockServer.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withSuccess("""
                        {"id":123456789,"expires_in":7199,"app_id":9999}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.verify("token-abc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SOCIAL_AUTH_FAILED);
    }

    @Test
    @DisplayName("카카오가 토큰을 거부하면 SOCIAL_AUTH_FAILED")
    void 제공자_거부() {
        mockServer.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.verify("bad-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SOCIAL_AUTH_FAILED);
    }
}
