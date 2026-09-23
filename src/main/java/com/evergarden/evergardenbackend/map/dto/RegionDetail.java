package com.evergarden.evergardenbackend.map.dto;

import com.evergarden.evergardenbackend.garden.dto.GardenObjectResponse;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import java.util.List;

/** 명세의 {@code RegionDetail} 스키마 — {@code RegionSummary}에 중심좌표·내 방문 여부·해금 가능 오브젝트를 더한 모양. */
public record RegionDetail(
        String code,
        String name,
        RegionLevel level,
        Double centerLat,
        Double centerLng,
        boolean visited,
        int visitCount,
        List<GardenObjectResponse> availableGardenObjects) {
}
