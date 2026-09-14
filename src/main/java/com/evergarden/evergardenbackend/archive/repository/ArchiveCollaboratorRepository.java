package com.evergarden.evergardenbackend.archive.repository;

import com.evergarden.evergardenbackend.archive.entity.ArchiveCollaborator;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveCollaboratorRepository extends JpaRepository<ArchiveCollaborator, Long> {
}
