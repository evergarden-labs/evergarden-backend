package com.evergarden.evergardenbackend.archive.dto;

import com.evergarden.evergardenbackend.archive.entity.ArchiveTheme;
import com.evergarden.evergardenbackend.archive.entity.CollaborationStatus;
import com.evergarden.evergardenbackend.archive.entity.CollaboratorRole;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 명세의 {@code ArchiveSummary} 스키마. */
public record ArchiveSummary(
        Long archiveId,
        String title,
        ArchiveTheme theme,
        String primaryColor,
        String coverImageUrl,
        LocalDate startDate,
        LocalDate endDate,
        int itemCount,
        CollaborationStatus collaborationStatus,
        CollaboratorRole myRole,
        Long linkedTripId,
        LocalDateTime createdAt) {
}
