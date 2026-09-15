package com.evergarden.evergardenbackend.archive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /posts/{postId}/archive/import}(ARCH-16)의 요청 본문. */
public record ArchiveImportRequest(@NotBlank @Size(min = 1, max = 60) String title) {
}
