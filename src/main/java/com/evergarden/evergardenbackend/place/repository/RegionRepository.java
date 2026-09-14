package com.evergarden.evergardenbackend.place.repository;

import com.evergarden.evergardenbackend.place.entity.Region;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, String> {
}
