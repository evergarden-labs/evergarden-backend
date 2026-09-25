package com.evergarden.evergardenbackend.auth.client.dto;

/**
 * 네이버 {@code /v1/nid/me} 응답. 검증 전용 엔드포인트가 없어, 이 호출이 성공하고
 * {@code resultcode}가 {@code "00"}이면 유효한 토큰으로 간주한다(ADR-062).
 *
 * @param resultcode {@code "00"}이면 성공, 그 외는 실패(예: {@code "024"} 인증 실패)
 * @param response   성공 시에만 채워지는 프로필. 실패하면 {@code null}일 수 있다
 */
public record NaverProfileResponse(String resultcode, String message, NaverProfile response) {

    /** @param id 사용자 고유 식별자 */
    public record NaverProfile(String id) {
    }
}
