package com.evergarden.evergardenbackend.auth.client;

import com.evergarden.evergardenbackend.auth.client.dto.NaverProfileResponse;
import com.evergarden.evergardenbackend.auth.config.NaverOAuthProperties;
import com.evergarden.evergardenbackend.auth.entity.SocialProvider;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 네이버 액세스 토큰 검증(ADR-062).
 *
 * <p>네이버는 토큰이 우리 앱에 발급된 것인지 확인할 전용 엔드포인트가 없다.
 * 프로필 조회가 {@code resultcode == "00"}으로 성공하면 유효한 토큰으로 간주한다 — 제한 사항.
 */
@Component
public class NaverAuthClient implements SocialAuthClient {

    private static final String SUCCESS_CODE = "00";

    private final RestClient restClient;
    private final NaverOAuthProperties properties;

    public NaverAuthClient(RestClient socialAuthRestClient, NaverOAuthProperties properties) {
        this.restClient = socialAuthRestClient;
        this.properties = properties;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.NAVER;
    }

    @Override
    public String verify(String socialAccessToken) {
        NaverProfileResponse response;
        try {
            response = restClient.get()
                    .uri(properties.userInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + socialAccessToken)
                    .retrieve()
                    .body(NaverProfileResponse.class);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED);
        }

        if (response == null || !SUCCESS_CODE.equals(response.resultcode())
                || response.response() == null || response.response().id() == null) {
            throw new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED);
        }
        return response.response().id();
    }
}
