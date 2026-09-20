package com.evergarden.evergardenbackend.place.client.dto;

import java.util.List;

/** {@code areaBasedSyncList2} 한 페이지 결과. */
public record AreaBasedSyncPage(List<SyncAreaBasedItem> items, int totalCount) {
}
