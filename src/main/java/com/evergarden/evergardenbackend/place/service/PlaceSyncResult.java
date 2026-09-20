package com.evergarden.evergardenbackend.place.service;

import java.util.List;

/**
 * 관광지 초기 시딩 결과. 좌표가 없거나 법정동코드가 우리 {@code Region}과 안 맞는
 * 항목은 저장하지 않고 {@code skippedContentIds}에 남긴다.
 */
public record PlaceSyncResult(int created, int updated, List<String> skippedContentIds) {
}
