package com.evergarden.evergardenbackend.garden.dto;

import com.evergarden.evergardenbackend.garden.entity.UserGardenObject;
import java.time.LocalDateTime;

/** 명세의 {@code UserGardenObject} 스키마 — 사용자가 실제로 해금하고 키운 것. */
public record UserGardenObjectResponse(
        Long userGardenObjectId,
        GardenObjectResponse gardenObject,
        int stage,
        Short positionX,
        Short positionY,
        LocalDateTime unlockedAt,
        LocalDateTime lastGrownAt) {

    public static UserGardenObjectResponse of(UserGardenObject entity) {
        return new UserGardenObjectResponse(
                entity.getId(), GardenObjectResponse.of(entity.getGardenObject()), entity.getStage(),
                entity.getPositionX(), entity.getPositionY(), entity.getUnlockedAt(), entity.getLastGrownAt());
    }
}
