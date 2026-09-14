package com.evergarden.evergardenbackend.garden.repository;

import com.evergarden.evergardenbackend.garden.entity.UserGardenObject;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGardenObjectRepository extends JpaRepository<UserGardenObject, Long> {
}
