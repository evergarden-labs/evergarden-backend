package com.evergarden.evergardenbackend.archive.repository;

import com.evergarden.evergardenbackend.archive.entity.Archive;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveRepository extends JpaRepository<Archive, Long> {
}
