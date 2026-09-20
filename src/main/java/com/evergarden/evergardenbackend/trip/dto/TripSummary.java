package com.evergarden.evergardenbackend.trip.dto;

import com.evergarden.evergardenbackend.place.dto.RegionSummary;
import com.evergarden.evergardenbackend.trip.entity.TripStatus;
import java.time.LocalDate;
import java.util.List;

/** 명세의 {@code TripSummary} 스키마. */
public record TripSummary(
        Long tripId,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        TripStatus status,
        List<RegionSummary> regions,
        int placeCount,
        String thumbnailUrl,
        Long linkedArchiveId) {
}
