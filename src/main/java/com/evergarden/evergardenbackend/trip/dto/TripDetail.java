package com.evergarden.evergardenbackend.trip.dto;

import com.evergarden.evergardenbackend.place.dto.RegionSummary;
import com.evergarden.evergardenbackend.trip.entity.TripStatus;
import java.time.LocalDate;
import java.util.List;

/**
 * 명세의 {@code TripDetail} 스키마 — {@code TripSummary}에 {@code days}·{@code originTripId}를
 * 더한 모양이다. 아카이브 쪽과 같은 이유로 상속 대신 필드를 그대로 펼친다.
 */
public record TripDetail(
        Long tripId,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        TripStatus status,
        List<RegionSummary> regions,
        int placeCount,
        String thumbnailUrl,
        Long linkedArchiveId,
        List<TripDay> days,
        Long originTripId) {
}
