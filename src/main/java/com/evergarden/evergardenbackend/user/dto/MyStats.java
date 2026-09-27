package com.evergarden.evergardenbackend.user.dto;

/**
 * 명세의 {@code MyStats} 스키마(ADR-034). 마이페이지(MY-01)가 실제로 값을 채운다.
 * 이 도메인(온보딩)에서는 항상 {@code null}로 둔다 — {@code MyProfile.stats}는 명세상 옵션이다.
 */
public record MyStats(
        int archiveCount,
        int tripCount,
        int visitedRegionCount,
        int gardenUnlockedCount,
        int gardenTotalCount,
        int postCount,
        int receivedLikeCount,
        int sealedCapsuleCount,
        int unlockableCapsuleCount) {
}
