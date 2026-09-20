package com.evergarden.evergardenbackend.trip.repository;

import com.evergarden.evergardenbackend.trip.entity.Trip;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findByOwner_Id(Long userId);
}
