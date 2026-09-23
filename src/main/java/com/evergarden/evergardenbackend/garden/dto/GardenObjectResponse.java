package com.evergarden.evergardenbackend.garden.dto;

import com.evergarden.evergardenbackend.garden.entity.GardenObject;
import com.evergarden.evergardenbackend.garden.entity.GardenObjectType;
import com.evergarden.evergardenbackend.place.dto.RegionSummary;

/** 명세의 {@code GardenObject} 스키마 — 도감 정보. 사용자가 가졌는지와 무관한 마스터 데이터다. */
public record GardenObjectResponse(
        Long gardenObjectId,
        String name,
        GardenObjectType type,
        String imageUrl,
        int maxStage,
        RegionSummary region) {

    public static GardenObjectResponse of(GardenObject entity) {
        return new GardenObjectResponse(
                entity.getId(), entity.getName(), entity.getType(), entity.getImageUrl(), entity.getMaxStage(),
                entity.getRegion() != null ? RegionSummary.of(entity.getRegion()) : null);
    }
}
