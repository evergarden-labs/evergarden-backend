package com.evergarden.evergardenbackend.place.dto;

import com.evergarden.evergardenbackend.place.entity.Place;
import java.util.List;

/**
 * {@code GET /places/{placeId}}(PLAN-07)의 응답.
 *
 * @param useTime  이용 시간. 콘텐츠랩 원문 그대로다(ADR-049) — 서버가 요일별로 파싱하지 않는다
 * @param restDate 휴무일. 콘텐츠랩 원문 그대로
 * @param imageUrls 관광 사진 정보(공공저작물). 아직 라이브 상세조회를 붙이지 않아 항상 빈 배열이다 —
 *                  {@code useTime}·{@code restDate}·{@code overview}와 함께
 *                  docs/decisions.md에 후속 작업으로 남겨뒀다
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
                List.of());
    }
}
