package com.evergarden.evergardenbackend.map.dto;

import com.evergarden.evergardenbackend.garden.dto.GardenObjectResponse;
import com.evergarden.evergardenbackend.garden.entity.RewardStatus;
import com.evergarden.evergardenbackend.place.dto.RegionSummary;
import java.time.LocalDateTime;

/** 명세의 {@code VisitReward} 스키마. */
public record VisitReward(
        Long visitId,
        RegionSummary region,
        RewardStatus rewardStatus,
        GardenObjectResponse gardenObject,
        Integer previousStage,
        Integer currentStage,
        LocalDateTime nextAvailableAt) {
}
