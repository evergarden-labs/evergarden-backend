package com.evergarden.evergardenbackend.archive.dto;

import com.evergarden.evergardenbackend.archive.entity.ArchiveLayoutSlot;
import com.evergarden.evergardenbackend.archive.entity.ArchiveTheme;
import com.evergarden.evergardenbackend.archive.entity.CollaborationStatus;
import com.evergarden.evergardenbackend.archive.entity.CollaboratorRole;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 명세의 {@code ArchiveDetail} 스키마 — {@code ArchiveSummary}에 {@code items}·
 * {@code collaborators}·{@code originArchiveId}를 더한 모양이다. OpenAPI의 {@code allOf}를
 * 자바에서 상속으로 표현하는 대신, 응답 JSON 모양이 같도록 필드를 그대로 펼쳤다.
 */
public record ArchiveDetail(
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
        LocalDateTime createdAt,
        List<ArchiveItemResponse> items,
        List<CollaboratorResponse> collaborators,
        Long originArchiveId,
        List<ArchiveLayoutSlot> layoutTemplate) {
}
