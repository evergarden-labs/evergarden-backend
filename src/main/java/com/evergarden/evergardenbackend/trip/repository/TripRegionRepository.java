package com.evergarden.evergardenbackend.trip.repository;

import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.entity.TripRegion;
import com.evergarden.evergardenbackend.trip.entity.TripRegionId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRegionRepository extends JpaRepository<TripRegion, TripRegionId> {

    List<TripRegion> findByTrip(Trip trip);
}
