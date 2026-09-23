package com.evergarden.evergardenbackend.map.dto;

import com.evergarden.evergardenbackend.garden.entity.RewardStatus;
import com.evergarden.evergardenbackend.place.dto.RegionSummary;
import java.time.LocalDateTime;

/**
 * 명세의 {@code RegionVisitResult} 스키마.
 *
 * <p>{@code reward}는 {@code rewardStatus}가 {@code NONE}이면 {@code null}이다 —
 * {@code GET /region-visits/{visitId}/reward}(GARDEN-02)와 달리 여기선 보상이
 * 없으면 아예 안 담는다.
 */
public record RegionVisitResult(
        Long visitId,
        RegionSummary region,
        LocalDateTime verifiedAt,
        boolean isFirstVisit,
        RewardStatus rewardStatus,
        VisitReward reward) {
}
