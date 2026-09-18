package com.evergarden.evergardenbackend.place.client.dto;

import java.util.List;

/** {@code areaBasedList2} 한 페이지 결과. {@code totalCount}로 마저 받을 페이지가 있는지 판단한다. */
public record AreaBasedPage(List<AreaBasedItem> items, int totalCount) {
}
