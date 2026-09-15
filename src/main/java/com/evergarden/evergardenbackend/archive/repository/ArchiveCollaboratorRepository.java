package com.evergarden.evergardenbackend.archive.repository;

import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveCollaborator;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveCollaboratorRepository extends JpaRepository<ArchiveCollaborator, Long> {

    List<ArchiveCollaborator> findByArchive(Archive archive);

    Optional<ArchiveCollaborator> findByArchiveAndUser_Id(Archive archive, Long userId);
}
