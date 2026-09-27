package com.evergarden.evergardenbackend.auth.client;

import com.evergarden.evergardenbackend.auth.client.dto.GoogleTokenInfo;
import com.evergarden.evergardenbackend.auth.config.GoogleOAuthProperties;
import com.evergarden.evergardenbackend.auth.entity.SocialProvider;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 구글 액세스 토큰 검증(ADR-062) — {@code tokeninfo} 하나로 발급 대상·사용자 식별자를 함께 확인한다. */
@Component
public class GoogleAuthClient implements SocialAuthClient {

    private final RestClient restClient;
    private final GoogleOAuthProperties properties;

    public GoogleAuthClient(RestClient socialAuthRestClient, GoogleOAuthProperties properties) {
        this.restClient = socialAuthRestClient;
        this.properties = properties;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public String verify(String socialAccessToken) {
        GoogleTokenInfo tokenInfo;
        try {
            tokenInfo = restClient.get()
                    .uri(properties.tokenInfoUri() + "?access_token={token}", socialAccessToken)
                    .retrieve()
                    .body(GoogleTokenInfo.class);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED);
        }

        if (tokenInfo == null || tokenInfo.sub() == null
                || !properties.clientId().equals(tokenInfo.aud())) {
            // aud 불일치는 남의 앱용으로 발급된 토큰이라는 뜻 — 검증 실패와 같게 다룬다
            throw new BusinessException(ErrorCode.SOCIAL_AUTH_FAILED);
        }
        return tokenInfo.sub();
    }
}
