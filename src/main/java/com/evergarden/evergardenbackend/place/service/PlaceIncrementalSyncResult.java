package com.evergarden.evergardenbackend.place.service;

import java.util.List;

/**
 * 일일 증분 동기화 결과. {@code showflag=0}(콘텐츠가 내려감)은 이번 구현 범위에서
 * 삭제·숨김 처리를 하지 않고 그냥 건너뛴다 — {@code ignoredDelisted}로 개수만 남긴다
 * (docs/decisions.md 참고).
 */
public record PlaceIncrementalSyncResult(
        int created, int updated, int ignoredDelisted, List<String> skippedContentIds) {
}
