package com.evergarden.evergardenbackend.place.dto;

import com.evergarden.evergardenbackend.place.entity.Place;

/** 명세의 {@code PlaceSummary} 스키마. */
public record PlaceSummary(
        Long placeId,
        String contentId,
        String contentTypeId,
        String title,
        String thumbnailUrl,
        double lat,
        double lng,
        RegionSummary region) {

    public static PlaceSummary of(Place place) {
        return new PlaceSummary(
                place.getId(), place.getContentId(), place.getContentTypeId(), place.getTitle(),
                place.getThumbnailUrl(), place.getLat().doubleValue(), place.getLng().doubleValue(),
                RegionSummary.of(place.getRegion()));
    }
}
