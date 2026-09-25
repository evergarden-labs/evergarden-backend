package com.evergarden.evergardenbackend.auth.client;

import com.evergarden.evergardenbackend.auth.client.dto.KakaoTokenInfo;
import com.evergarden.evergardenbackend.auth.config.KakaoOAuthProperties;
import com.evergarden.evergardenbackend.auth.entity.SocialProvider;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 카카오 액세스 토큰 검증(ADR-062) — {@code access_token_info} 하나로 발급 대상·사용자 식별자를 함께 확인한다. */
@Component
public class KakaoAuthClient implements SocialAuthClient {

    private final RestClient restClient;
    private final KakaoOAuthProperties properties;

    public KakaoAuthClient(RestClient socialAuthRestClient, KakaoOAuthProperties properties) {
        this.restClient = socialAuthRestClient;
        this.properties = properties;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public String verify(String socialAccessToken) {
        KakaoTokenInfo tokenInfo;
        try {
            tokenInfo = restClient.get()
                    .uri(properties.tokenInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + socialAccessToken)
                    .retrieve()
                    .body(KakaoTokenInfo.class);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED);
        }

        if (tokenInfo == null || tokenInfo.id() == null
                || !properties.appId().equals(String.valueOf(tokenInfo.appId()))) {
            // app_id 불일치는 남의 앱용으로 발급된 토큰이라는 뜻 — 검증 실패와 같게 다룬다
            throw new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED);
        }
        return String.valueOf(tokenInfo.id());
    }
}
