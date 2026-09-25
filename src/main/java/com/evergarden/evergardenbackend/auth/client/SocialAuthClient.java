package com.evergarden.evergardenbackend.auth.client;

import com.evergarden.evergardenbackend.auth.entity.SocialProvider;
import com.evergarden.evergardenbackend.global.exception.BusinessException;

/**
 * 소셜 제공자별 액세스 토큰 검증(ADR-010 · ADR-062).
 *
 * <p>앱이 SDK로 받은 액세스 토큰이 우리 앱에 발급된 것인지 확인하고, 검증되면 그 제공자
 * 안에서 유일한 사용자 식별자를 돌려준다. {@code SocialAccount.providerUserId}로 그대로 저장된다.
 *
 * <p>구글·카카오는 토큰 검증 엔드포인트 하나로 발급 대상 확인과 식별자 조회를 같이 끝낸다.
 * 네이버는 검증 전용 엔드포인트가 없어 프로필 조회 성공 여부로 대신한다(ADR-062).
 */
public interface SocialAuthClient {

    /** 이 클라이언트가 다루는 제공자. */
    SocialProvider provider();

    /**
     * 토큰을 검증하고 제공자 쪽 사용자 식별자를 돌려준다.
     *
     * @throws BusinessException {@code SOCIAL_AUTH_FAILED} — 토큰이 무효하거나, 우리 앱에
     *                            발급된 게 아니거나, 제공자 API 호출 자체가 실패한 경우
     */
    String verify(String socialAccessToken);
}
