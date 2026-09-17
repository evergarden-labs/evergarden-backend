package com.evergarden.evergardenbackend.place.dto;

import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;

/** 명세의 {@code RegionSummary} 스키마. */
public record RegionSummary(String code, String name, RegionLevel level) {

    public static RegionSummary of(Region region) {
        return new RegionSummary(region.getCode(), region.getName(), region.getLevel());
    }
}
