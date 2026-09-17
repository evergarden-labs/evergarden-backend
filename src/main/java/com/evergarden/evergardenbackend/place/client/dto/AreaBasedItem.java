package com.evergarden.evergardenbackend.place.client.dto;

/**
 * {@code areaBasedList2} 응답 항목. TourAPI 필드명을 그대로 쓴다(소문자 붙여쓰기 포함) —
 * 매핑 애노테이션 없이 JSON 필드명과 그대로 맞아떨어지게 하려고.
 */
public record AreaBasedItem(
        String contentid,
        String contenttypeid,
        String title,
        String addr1,
        String tel,
        String mapx,
        String mapy,
        String firstimage2,
        String lDongRegnCd,
        String lDongSignguCd) {
}
