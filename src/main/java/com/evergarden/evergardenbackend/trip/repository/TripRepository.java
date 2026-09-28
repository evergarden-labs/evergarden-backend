package com.evergarden.evergardenbackend.trip.repository;

import com.evergarden.evergardenbackend.trip.entity.Trip;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByOwner_Id(Long userId);

    /** 내가 만든 여행 일정 수. 마이페이지의 {@code tripCount}(MY-01). */
    long countByOwner_Id(Long userId);
}
