package com.evergarden.evergardenbackend.trip.dto;

import com.evergarden.evergardenbackend.place.dto.PlaceSummary;

/** 명세의 {@code TripPlace} 스키마. 엔티티 {@code TripPlace}와 이름이 겹쳐 Response를 붙인다. */
public record TripPlaceResponse(
        Long tripPlaceId,
        PlaceSummary place,
        short dayNumber,
        short sortOrder,
        String memo) {
}
