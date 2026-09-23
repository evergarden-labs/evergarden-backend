package com.evergarden.evergardenbackend.garden.dto;

import java.util.List;

/** 명세의 {@code Garden} 스키마. 정원은 사용자당 하나라 지역 구분이 없다(ADR-037). */
public record Garden(
        List<UserGardenObjectResponse> objects,
        int unlockedCount,
        int totalCount) {
}
