package com.evergarden.evergardenbackend.archive.repository;

import com.evergarden.evergardenbackend.archive.entity.ArchiveItem;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveItemRepository extends JpaRepository<ArchiveItem, Long> {
}
