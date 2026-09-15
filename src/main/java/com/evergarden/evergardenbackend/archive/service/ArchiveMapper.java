package com.evergarden.evergardenbackend.archive.service;

import com.evergarden.evergardenbackend.archive.dto.ArchiveDetail;
import com.evergarden.evergardenbackend.archive.dto.ArchiveItemResponse;
import com.evergarden.evergardenbackend.archive.dto.ArchiveSummary;
import com.evergarden.evergardenbackend.archive.dto.CollaboratorResponse;
import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveCollaborator;
import com.evergarden.evergardenbackend.archive.entity.ArchiveItem;
import com.evergarden.evergardenbackend.archive.entity.CollaboratorRole;
import com.evergarden.evergardenbackend.media.dto.MediaResponse;
import com.evergarden.evergardenbackend.media.service.MediaMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** {@link Archive} 응답 변환. 목록은 {@link ArchiveSummary}, 상세는 {@link ArchiveDetail}. */
@Component
@RequiredArgsConstructor
public class ArchiveMapper {

    private final MediaMapper mediaMapper;

    public ArchiveSummary toSummary(Archive archive, int itemCount, Long viewerId) {
        return new ArchiveSummary(
                archive.getId(), archive.getTitle(), archive.getTheme(), archive.getPrimaryColor(),
                coverImageUrl(archive), archive.getStartDate(), archive.getEndDate(), itemCount,
                archive.getCollaborationStatus(), myRole(archive, viewerId), linkedTripId(archive),
                archive.getCreatedAt());
    }

    public ArchiveDetail toDetail(Archive archive, List<ArchiveItem> items,
                                  List<ArchiveCollaborator> collaborators, Long viewerId) {
        List<ArchiveItemResponse> itemResponses = items.stream()
                .map(item -> toItemResponse(archive, item))
                .toList();
        List<CollaboratorResponse> collaboratorResponses = collaborators.stream()
                .map(CollaboratorResponse::of)
                .toList();
        return new ArchiveDetail(
                archive.getId(), archive.getTitle(), archive.getTheme(), archive.getPrimaryColor(),
                coverImageUrl(archive), archive.getStartDate(), archive.getEndDate(), items.size(),
                archive.getCollaborationStatus(), myRole(archive, viewerId), linkedTripId(archive),
                archive.getCreatedAt(), itemResponses, collaboratorResponses,
                archive.getOriginArchive() != null ? archive.getOriginArchive().getId() : null);
    }

    public ArchiveItemResponse toItemResponse(Archive archive, ArchiveItem item) {
        boolean isCover = archive.getCoverItem() != null && archive.getCoverItem().getId().equals(item.getId());
        return new ArchiveItemResponse(item.getId(), mediaMapper.toResponse(item.getMedia()),
                item.getSortOrder(), item.getLayout(), item.getCaption(), isCover);
    }

    /** 대표 사진(ARCH-08)의 썸네일. 썸네일이 없으면(HEIC 등 생성 실패) 원본으로 대신한다. */
    private String coverImageUrl(Archive archive) {
        ArchiveItem cover = archive.getCoverItem();
        if (cover == null) {
            return null;
        }
        MediaResponse media = mediaMapper.toResponse(cover.getMedia());
        return media.thumbnailUrl() != null ? media.thumbnailUrl() : media.url();
    }

    private CollaboratorRole myRole(Archive archive, Long viewerId) {
        return archive.isOwnedBy(viewerId) ? CollaboratorRole.OWNER : CollaboratorRole.EDITOR;
    }

    private Long linkedTripId(Archive archive) {
        return archive.getTrip() != null ? archive.getTrip().getId() : null;
    }
}
