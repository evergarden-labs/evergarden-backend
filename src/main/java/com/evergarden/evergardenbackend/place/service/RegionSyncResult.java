package com.evergarden.evergardenbackend.place.service;

import java.util.List;

/**
 * 지역 시딩 결과. 좌표를 못 찾은 지역은 저장하지 않고 {@code failedQueries}에 남긴다 —
 * {@code Region.centerLat}·{@code centerLng}가 NOT NULL이라 좌표 없이는 저장할 수 없다.
 */
public record RegionSyncResult(int provinceCount, int districtCount, List<String> failedQueries) {
}
