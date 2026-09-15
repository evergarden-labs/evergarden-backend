package com.evergarden.evergardenbackend.archive.dto;

import com.evergarden.evergardenbackend.archive.entity.ArchiveTheme;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code PATCH /archives/{archiveId}}(ARCH-02)의 요청 본문. 보내지 않은 필드는 바뀌지 않는다. */
public record ArchiveUpdateRequest(
        @Size(min = 1, max = 60) String title,
        ArchiveTheme theme,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String primaryColor) {

    public boolean isEmpty() {
        return title == null && theme == null && primaryColor == null;
    }
}
