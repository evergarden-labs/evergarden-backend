package com.evergarden.evergardenbackend.garden.repository;

import com.evergarden.evergardenbackend.garden.entity.GardenObject;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GardenObjectRepository extends JpaRepository<GardenObject, Long> {
}
