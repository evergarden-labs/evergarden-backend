package com.evergarden.evergardenbackend.archive.dto;

import com.evergarden.evergardenbackend.archive.entity.ArchiveLayout;
import com.evergarden.evergardenbackend.media.dto.MediaResponse;

/** 명세의 {@code ArchiveItem} 스키마. */
public record ArchiveItemResponse(
        Long itemId,
        MediaResponse media,
        short sortOrder,
        ArchiveLayout layout,
        String caption,
        boolean isCover) {
}
