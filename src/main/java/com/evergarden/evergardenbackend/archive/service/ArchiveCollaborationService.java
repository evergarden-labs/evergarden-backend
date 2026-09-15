package com.evergarden.evergardenbackend.archive.service;

import com.evergarden.evergardenbackend.archive.dto.ArchiveDetail;
import com.evergarden.evergardenbackend.archive.dto.CollaboratorResponse;
import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveCollaborator;
import com.evergarden.evergardenbackend.archive.entity.ArchiveItem;
import com.evergarden.evergardenbackend.archive.entity.CollaboratorStatus;
import com.evergarden.evergardenbackend.archive.repository.ArchiveCollaboratorRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveItemRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아카이브 공동 편집 초대·수락·거절·나가기·종료(ARCH-10·11·13·14·18).
 *
 * <p>초대 알림(NOTI-02)은 알림 도메인이 아직 없어서 여기서 만들지 않는다.
 * 알림 도메인을 구현할 때 이 서비스가 이벤트를 발행하도록 이어붙이면 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ArchiveCollaborationService {

    private final ArchiveRepository archiveRepository;
    private final ArchiveItemRepository archiveItemRepository;
    private final ArchiveCollaboratorRepository collaboratorRepository;
    private final UserRepository userRepository;
    private final ArchiveAccessGuard accessGuard;
    private final ArchiveMapper archiveMapper;

    /** 소유자만 초대할 수 있다. 처음 초대하면 공동 편집이 열린다(ARCH-10). */
    public CollaboratorResponse invite(Long userId, Long archiveId, Long inviteeId) {
        Archive archive = findArchive(archiveId);
        accessGuard.checkOwnerAndEditable(archive, userId);

        User invitee = userRepository.findById(inviteeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ArchiveCollaborator collaborator = collaboratorRepository
                .findByArchiveAndUser_Id(archive, inviteeId)
                .map(existing -> reinviteOrReject(existing))
                .orElseGet(() -> collaboratorRepository.save(
                        ArchiveCollaborator.invite(archive, invitee, LocalDateTime.now())));

        archive.openCollaboration();
        return CollaboratorResponse.of(collaborator);
    }

    /** 초대를 수락한다(ARCH-11). */
    public CollaboratorResponse accept(Long userId, Long archiveId) {
        Archive archive = findArchive(archiveId);
        ArchiveCollaborator collaborator = invitedCollaborator(archive, userId);
        if (!archive.isEditable()) {
            throw new BusinessException(ErrorCode.COLLABORATION_CLOSED);
        }
        collaborator.accept(LocalDateTime.now());
        return CollaboratorResponse.of(collaborator);
    }

    /**
     * 초대를 거절한다(ARCH-18). 초대 자체를 지운다 — 나중에 같은 아카이브에서
     * 다시 초대받을 수 있어야 하는데, {@code (archive_id, user_id)}가 유니크라
     * 행을 남겨두면 재초대 때 값을 재사용해야 하는 번거로움이 생긴다.
     */
    public void decline(Long userId, Long archiveId) {
        Archive archive = findArchive(archiveId);
        ArchiveCollaborator collaborator = invitedCollaborator(archive, userId);
        collaboratorRepository.delete(collaborator);
    }

    /** 참여 중인 아카이브에서 스스로 빠진다(ARCH-13). 소유자는 나갈 수 없다. */
    public void leave(Long userId, Long archiveId) {
        Archive archive = findArchive(archiveId);
        if (archive.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        ArchiveCollaborator collaborator = collaboratorRepository.findByArchiveAndUser_Id(archive, userId)
                .filter(ArchiveCollaborator::canEdit)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_COLLABORATOR));
        collaborator.leave();
    }

    /** 공동 편집을 끝낸다(ARCH-14). 소유자만, 이미 종료됐으면 다시 못 끝낸다. */
    public ArchiveDetail close(Long userId, Long archiveId) {
        Archive archive = findArchive(archiveId);
        accessGuard.checkOwnerAndEditable(archive, userId);
        archive.closeCollaboration();
        return toDetail(archive, userId);
    }

    /** 기존 행이 있으면 상태에 따라 재초대하거나 중복 오류를 던진다. */
    private ArchiveCollaborator reinviteOrReject(ArchiveCollaborator existing) {
        return switch (existing.getStatus()) {
            case INVITED -> throw new BusinessException(ErrorCode.ALREADY_INVITED);
            case JOINED -> throw new BusinessException(ErrorCode.ALREADY_COLLABORATOR);
            case LEFT -> {
                existing.reinvite(LocalDateTime.now());
                yield existing;
            }
        };
    }

    /** 초대받은 적 있는지 확인한다. LEFT는 더 이상 "지금 초대받은 상태"가 아니다. */
    private ArchiveCollaborator invitedCollaborator(Archive archive, Long userId) {
        ArchiveCollaborator collaborator = collaboratorRepository.findByArchiveAndUser_Id(archive, userId)
                .filter(c -> c.getStatus() != CollaboratorStatus.LEFT)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_COLLABORATOR));
        if (collaborator.getStatus() == CollaboratorStatus.JOINED) {
            throw new BusinessException(ErrorCode.ALREADY_COLLABORATOR);
        }
        return collaborator;
    }

    private Archive findArchive(Long archiveId) {
        return archiveRepository.findById(archiveId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARCHIVE_NOT_FOUND));
    }

    private ArchiveDetail toDetail(Archive archive, Long userId) {
        List<ArchiveItem> items = archiveItemRepository.findByArchiveOrderBySortOrderAsc(archive);
        List<ArchiveCollaborator> collaborators = collaboratorRepository.findByArchive(archive);
        return archiveMapper.toDetail(archive, items, collaborators, userId);
    }
}
