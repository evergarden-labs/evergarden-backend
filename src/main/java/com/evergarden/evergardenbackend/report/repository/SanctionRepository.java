package com.evergarden.evergardenbackend.report.repository;

import com.evergarden.evergardenbackend.report.entity.Sanction;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SanctionRepository extends JpaRepository<Sanction, Long> {
}
