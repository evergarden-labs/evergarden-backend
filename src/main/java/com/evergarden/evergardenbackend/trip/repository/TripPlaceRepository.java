package com.evergarden.evergardenbackend.trip.repository;

import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.entity.TripPlace;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripPlaceRepository extends JpaRepository<TripPlace, Long> {

    List<TripPlace> findByTripOrderByDayNumberAscSortOrderAsc(Trip trip);

    long countByTrip(Trip trip);

    Optional<TripPlace> findByIdAndTrip(Long id, Trip trip);

    /** 자리를 밀 때(끼워넣기) 씀 — 높은 순서부터 처리해야 유니크 제약과 안 부딪힌다. */
    List<TripPlace> findByTripAndDayNumberOrderBySortOrderDesc(Trip trip, short dayNumber);

    /** 빈자리를 당길 때(제거·이동 후) 씀 — 낮은 순서부터 처리해야 유니크 제약과 안 부딪힌다. */
    List<TripPlace> findByTripAndDayNumberOrderBySortOrderAsc(Trip trip, short dayNumber);

    long countByTripAndDayNumber(Trip trip, short dayNumber);
}
