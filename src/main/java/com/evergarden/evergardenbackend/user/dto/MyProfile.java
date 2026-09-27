package com.evergarden.evergardenbackend.user.dto;

/**
 * 명세의 {@code MyProfile} 스키마. {@code completeOnboarding}(ONB-01)·{@code skipOnboarding}(ONB-02)이 쓴다.
 *
 * @param stats 마이페이지 기록 현황(ADR-034). 명세상 옵션이라 온보딩 응답에서는 항상 {@code null} —
 *              9개 지표 집계는 마이페이지(MY-01, {@code getMyProfile})의 몫이다
 */
public record MyProfile(
        Long userId,
        String nickname,
        String profileImageUrl,
        boolean onboardingCompleted,
        MyStats stats) {

    /** 온보딩 완료·건너뛰기 응답 전용 — {@code stats} 없이 만든다. */
    public static MyProfile withoutStats(
            Long userId, String nickname, String profileImageUrl, boolean onboardingCompleted) {
        return new MyProfile(userId, nickname, profileImageUrl, onboardingCompleted, null);
    }
}
