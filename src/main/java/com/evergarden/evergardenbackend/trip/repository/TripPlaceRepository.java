package com.evergarden.evergardenbackend.trip.repository;

import com.evergarden.evergardenbackend.trip.entity.TripPlace;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripPlaceRepository extends JpaRepository<TripPlace, Long> {
}
