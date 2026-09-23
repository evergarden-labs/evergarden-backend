package com.evergarden.evergardenbackend.place.client.dto;

/**
 * {@code locationBasedList2} 응답 항목. {@code areaBasedList2}와 같은 관광정보
 * 항목 모양이라 필요한 필드만 겹쳐 쓴다.
 *
 * <p>{@code lDongSignguCd}가 비어 있는 항목이 있다({@code PlaceQueryService}에서도 확인된 사실) —
 * 시/도로만 태그된 관광지라, 시군구까지는 못 정하고 시/도까지만 판정해야 할 수 있다.
 */
public record LocationBasedItem(
        String contentid,
        String contenttypeid,
        String title,
        String lDongRegnCd,
        String lDongSignguCd) {
}
