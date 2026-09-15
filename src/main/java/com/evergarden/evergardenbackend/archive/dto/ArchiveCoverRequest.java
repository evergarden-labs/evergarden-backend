package com.evergarden.evergardenbackend.archive.dto;

import jakarta.validation.constraints.NotNull;

/** {@code PUT /archives/{archiveId}/cover}(ARCH-08)의 요청 본문. */
public record ArchiveCoverRequest(@NotNull Long itemId) {
}
