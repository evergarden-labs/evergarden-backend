package com.evergarden.evergardenbackend.archive.dto;

import jakarta.validation.constraints.NotNull;

/** {@code POST /archives/{archiveId}/collaborators}(ARCH-10)의 요청 본문. */
public record CollaboratorInviteRequest(@NotNull Long userId) {
}
