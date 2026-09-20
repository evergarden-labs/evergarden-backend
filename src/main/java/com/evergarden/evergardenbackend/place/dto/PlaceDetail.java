package com.evergarden.evergardenbackend.place.dto;

import com.evergarden.evergardenbackend.place.entity.Place;
import java.util.List;

/**
 * {@code GET /places/{placeId}}(PLAN-07)의 응답.
 *
 * @param useTime   이용 시간. 콘텐츠랩 원문 그대로다(ADR-049) — 서버가 요일별로 파싱하지 않는다.
 *                  관광지·문화시설·음식점만 값이 채워진다 — 나머지 콘텐츠타입은 그 개념 자체가
 *                  달라(행사 기간, 체크인 시간 등) {@code null}이다(ADR-061)
 * @param restDate  휴무일. 콘텐츠랩 원문 그대로. {@code useTime}과 같은 이유로 일부 타입만 채워진다
 * @param imageUrls 관광 사진 정보(공공저작물). 없으면 빈 배열
 */
public record PlaceDetail(
        Long placeId, String contentId, String contentTypeId, String title, String thumbnailUrl,
        double lat, double lng, RegionSummary region,
        String addr, String tel, String overview, String useTime, String restDate, List<String> imageUrls) {

    public static PlaceDetail of(Place place) {
        return new PlaceDetail(
                place.getId(), place.getContentId(), place.getContentTypeId(), place.getTitle(),
                place.getThumbnailUrl(), place.getLat().doubleValue(), place.getLng().doubleValue(),
                RegionSummary.of(place.getRegion()),
                place.getAddr(), place.getTel(), place.getOverview(), place.getUseTime(), place.getRestDate(),
                place.getImageUrls() == null ? List.of() : place.getImageUrls());
    }
}
