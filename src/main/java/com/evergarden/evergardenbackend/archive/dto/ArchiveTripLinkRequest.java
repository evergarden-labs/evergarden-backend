package com.evergarden.evergardenbackend.archive.dto;

import jakarta.validation.constraints.NotNull;

/** {@code PUT /archives/{archiveId}/trip}(ARCH-17)의 요청 본문. */
public record ArchiveTripLinkRequest(@NotNull Long tripId) {
}
