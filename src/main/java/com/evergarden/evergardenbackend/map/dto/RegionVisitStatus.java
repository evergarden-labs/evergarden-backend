package com.evergarden.evergardenbackend.map.dto;

import com.evergarden.evergardenbackend.place.dto.RegionSummary;
import java.time.LocalDateTime;

/** 명세의 {@code RegionVisitStatus} 스키마. 지도 색칠용 — 미방문 지역도 {@code visited=false}로 담긴다. */
public record RegionVisitStatus(
        RegionSummary region,
        boolean visited,
        int visitCount,
        LocalDateTime lastVisitedAt) {
}
