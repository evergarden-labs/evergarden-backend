package com.evergarden.evergardenbackend.trip.dto;

import java.time.LocalDate;
import java.util.List;

/** 명세의 {@code TripDay} 스키마. 장소가 없는 날도 기간만큼 빠짐없이 들어간다. */
public record TripDay(int dayNumber, LocalDate date, List<TripPlaceResponse> places) {
}
