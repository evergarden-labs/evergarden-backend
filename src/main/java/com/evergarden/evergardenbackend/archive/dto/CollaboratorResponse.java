package com.evergarden.evergardenbackend.archive.dto;

import com.evergarden.evergardenbackend.archive.entity.ArchiveCollaborator;
import com.evergarden.evergardenbackend.archive.entity.CollaboratorRole;
import com.evergarden.evergardenbackend.archive.entity.CollaboratorStatus;
import java.time.LocalDateTime;

/** 명세의 {@code Collaborator} 스키마. */
public record CollaboratorResponse(
        Long userId,
        String nickname,
        String profileImageUrl,
        CollaboratorRole role,
        CollaboratorStatus status,
        LocalDateTime invitedAt,
        LocalDateTime joinedAt) {

    public static CollaboratorResponse of(ArchiveCollaborator collaborator) {
        return new CollaboratorResponse(
                collaborator.getUser().getId(),
                collaborator.getUser().getNickname(),
                collaborator.getUser().getProfileImageUrl(),
                collaborator.getRole(),
                collaborator.getStatus(),
                collaborator.getInvitedAt(),
                collaborator.getJoinedAt());
    }
}
