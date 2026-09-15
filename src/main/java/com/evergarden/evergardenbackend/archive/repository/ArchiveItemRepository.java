package com.evergarden.evergardenbackend.archive.repository;

import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchiveItemRepository extends JpaRepository<ArchiveItem, Long> {

    List<ArchiveItem> findByArchiveOrderBySortOrderAsc(Archive archive);

    long countByArchive(Archive archive);
}
