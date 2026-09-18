package com.evergarden.evergardenbackend.place.client.dto;

/**
 * {@code areaBasedSyncList2} 응답 항목. {@link AreaBasedItem}과 필드가 같고
 * {@code showflag}만 추가된다 — {@code "0"}이면 콘텐츠가 내려간 것이다.
 */
public record SyncAreaBasedItem(
        String contentid,
        String contenttypeid,
        String title,
        String addr1,
        String tel,
        String mapx,
        String mapy,
        String firstimage2,
        String lDongRegnCd,
        String lDongSignguCd,
        String showflag) {

    /** 지금 표출 중인 콘텐츠인지. {@code false}면 내려간 것이라 이번 동기화에서는 건드리지 않는다. */
    public boolean isVisible() {
        return "1".equals(showflag);
    }

    /** {@link com.evergarden.evergardenbackend.place.service.PlaceUpsertService}가 그대로 받아쓸 수 있는 모양으로. */
    public AreaBasedItem toAreaBasedItem() {
        return new AreaBasedItem(contentid, contenttypeid, title, addr1, tel, mapx, mapy, firstimage2,
                lDongRegnCd, lDongSignguCd);
    }
}
