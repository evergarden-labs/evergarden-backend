package com.evergarden.evergardenbackend.archive.dto;

import com.evergarden.evergardenbackend.archive.entity.ArchiveLayout;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** {@code PATCH /archives/{archiveId}/items/{itemId}}(ARCH-06/07). 보내지 않은 필드는 안 바뀐다. */
public record ArchiveItemUpdateRequest(
        @Min(1) Short sortOrder,
        ArchiveLayout layout,
        @Size(max = 300) String caption) {

    public boolean isEmpty() {
        return sortOrder == null && layout == null && caption == null;
    }
}
