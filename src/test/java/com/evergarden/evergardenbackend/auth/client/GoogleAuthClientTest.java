package com.evergarden.evergardenbackend.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.evergarden.evergardenbackend.auth.config.GoogleOAuthProperties;
import com.evergarden.evergardenbackend.auth.entity.SocialProvider;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 구글 토큰 검증(ADR-062) — 실제 HTTP 호출은 목 서버로 대체한다. */
class GoogleAuthClientTest {

    private static final String TOKEN_INFO_URI = "https://oauth2.googleapis.com/tokeninfo";
    private static final String CLIENT_ID = "our-client-id.apps.googleusercontent.com";

    private MockRestServiceServer mockServer;
    private GoogleAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new GoogleAuthClient(builder.build(), new GoogleOAuthProperties(TOKEN_INFO_URI, CLIENT_ID));
    }

    @Test
    @DisplayName("provider()는 GOOGLE")
    void provider() {
        assertThat(client.provider()).isEqualTo(SocialProvider.GOOGLE);
    }

    @Test
    @DisplayName("aud가 우리 client-id와 같으면 sub를 돌려준다")
    void 검증_성공() {
        mockServer.expect(requestTo(containsString(TOKEN_INFO_URI)))
                .andExpect(queryParam("access_token", "token-abc"))
                .andRespond(withSuccess("""
                        {"sub":"1234567890","aud":"our-client-id.apps.googleusercontent.com"}
                        """, MediaType.APPLICATION_JSON));

        String providerUserId = client.verify("token-abc");

        assertThat(providerUserId).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("aud가 다르면 SOCIAL_AUTH_FAILED — 남의 앱용으로 발급된 토큰이다")
    void aud_불일치() {
        mockServer.expect(requestTo(containsString(TOKEN_INFO_URI)))
                .andRespond(withSuccess("""
                        {"sub":"1234567890","aud":"other-app-client-id"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.verify("token-abc"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SOCIAL_AUTH_FAILED);
    }

    @Test
    @DisplayName("구글이 토큰을 거부하면 SOCIAL_AUTH_FAILED")
    void 제공자_거부() {
        mockServer.expect(requestTo(containsString(TOKEN_INFO_URI)))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.verify("bad-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SOCIAL_AUTH_FAILED);
    }
}
