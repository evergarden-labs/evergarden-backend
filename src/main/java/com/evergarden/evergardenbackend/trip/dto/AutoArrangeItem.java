package com.evergarden.evergardenbackend.trip.dto;

import com.evergarden.evergardenbackend.place.dto.PlaceSummary;

/** {@code TripPlaceOrderRequest.Item}과 같은 모양이라 그대로 {@code PUT /places/order}에 넘길 수 있다. */
public record AutoArrangeItem(Long tripPlaceId, short dayNumber, short sortOrder, PlaceSummary place) {
}
