package com.evergarden.evergardenbackend.trip.service;

import com.evergarden.evergardenbackend.place.dto.PlaceSummary;
import com.evergarden.evergardenbackend.place.dto.RegionSummary;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.trip.dto.TripDay;
import com.evergarden.evergardenbackend.trip.dto.TripDetail;
import com.evergarden.evergardenbackend.trip.dto.TripPlaceResponse;
import com.evergarden.evergardenbackend.trip.dto.TripSummary;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import com.evergarden.evergardenbackend.trip.entity.TripStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TripMapper {

    /**
     * @param tripPlaces 이 일정에 담긴 항목 전체. day·sortOrder 오름차순으로 정렬돼 있어야
     *                   {@code thumbnailUrl}("첫 번째의 대표 이미지")이 맞게 나온다
     */
    public TripSummary toSummary(Trip trip, List<Region> regions, List<TripPlace> tripPlaces, Long linkedArchiveId) {
        return new TripSummary(
                trip.getId(), trip.getTitle(), trip.getStartDate(), trip.getEndDate(),
                TripStatus.of(trip.getStartDate(), trip.getEndDate(), LocalDate.now()),
                regions.stream().map(RegionSummary::of).toList(),
                tripPlaces.size(),
                thumbnailUrl(tripPlaces),
                linkedArchiveId);
    }

    public TripDetail toDetail(Trip trip, List<Region> regions, List<TripPlace> tripPlaces, Long linkedArchiveId) {
        return new TripDetail(
                trip.getId(), trip.getTitle(), trip.getStartDate(), trip.getEndDate(),
                TripStatus.of(trip.getStartDate(), trip.getEndDate(), LocalDate.now()),
                regions.stream().map(RegionSummary::of).toList(),
                tripPlaces.size(),
                thumbnailUrl(tripPlaces),
                linkedArchiveId,
                buildDays(trip, tripPlaces),
                trip.getOriginTrip() != null ? trip.getOriginTrip().getId() : null);
    }

    private String thumbnailUrl(List<TripPlace> tripPlaces) {
        return tripPlaces.isEmpty() ? null : tripPlaces.get(0).getPlace().getThumbnailUrl();
    }

    /** 여행 기간만큼 일자를 전부 만들고, 장소가 없는 날은 빈 배열로 채운다. */
    private List<TripDay> buildDays(Trip trip, List<TripPlace> tripPlaces) {
        Map<Short, List<TripPlace>> byDay = tripPlaces.stream()
                .collect(Collectors.groupingBy(TripPlace::getDayNumber));

        List<TripDay> days = new ArrayList<>();
        int totalDays = trip.durationDays();
        for (short day = 1; day <= totalDays; day++) {
            LocalDate date = trip.getStartDate().plusDays(day - 1);
            List<TripPlaceResponse> places = byDay.getOrDefault(day, List.of()).stream()
                    .map(this::toPlaceResponse)
                    .toList();
            days.add(new TripDay(day, date, places));
        }
        return days;
    }

    /** {@code TripPlaceService}가 단건 응답(추가·수정)에도 그대로 재사용한다. */
    public TripPlaceResponse toPlaceResponse(TripPlace tripPlace) {
        return new TripPlaceResponse(
                tripPlace.getId(),
                PlaceSummary.of(tripPlace.getPlace()),
                tripPlace.getDayNumber(),
                tripPlace.getSortOrder(),
                tripPlace.getMemo());
    }
}
