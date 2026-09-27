package com.evergarden.evergardenbackend.user.dto;

import jakarta.validation.constraints.Pattern;

/**
 * {@code completeOnboarding}(ONB-01)·{@code updateMyProfile}(MY-02) 공통 요청 본문.
 * 명세는 {@code minProperties: 1}을 요구하지만 필드 하나짜리 애노테이션으론 못 걸어서
 * 각 서비스가 직접 확인한다({@code ReportRequest}의 교차 검증과 같은 이유).
 *
 * @param nickname        한글·영문·숫자 1~20자, 중복 불가(ADR-053 · ADR-025). 안 보내면 안 바꾼다.
 *                        길이 제한은 정규식 자체({@code {1,20}})에 있어 별도 {@code @Size}는 안 붙인다
 * @param profileImageUrl {@code null}을 보내면 기본 이미지로 되돌린다. 안 보내면 안 바꾼다
 */
public record ProfileUpdateRequest(
        @Pattern(regexp = "^[가-힣a-zA-Z0-9]{1,20}$") String nickname,
        String profileImageUrl) {
}
