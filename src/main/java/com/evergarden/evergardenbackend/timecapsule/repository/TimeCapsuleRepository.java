package com.evergarden.evergardenbackend.timecapsule.repository;

import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsule;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeCapsuleRepository extends JpaRepository<TimeCapsule, Long> {
}
