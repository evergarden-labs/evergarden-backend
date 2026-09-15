package com.evergarden.evergardenbackend.archive.service;

import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveCollaborator;
import com.evergarden.evergardenbackend.archive.repository.ArchiveCollaboratorRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 아카이브 소유자·공동편집자 판별을 이 클래스 하나로 모은다. 21개 오퍼레이션 대부분이
 * 같은 세 가지 질문(볼 수 있나·고칠 수 있나·소유자인가)만 하기 때문이다.
 *
 * <p>참여자가 아예 아닌 경우는 항상 {@code NOT_RESOURCE_OWNER}로 통일한다 — 명세의
 * {@code getArchive}가 "소유자도 공동 편집자도 아님"을 이 코드 하나로만 표현하고 있어서다.
 */
@Component
@RequiredArgsConstructor
public class ArchiveAccessGuard {

    private final ArchiveCollaboratorRepository collaboratorRepository;

    /** 조회 권한. 공동 편집이 종료돼도 참여한 적 있으면 통과한다(ADR-017). */
    public void checkViewable(Archive archive, Long userId) {
        if (!isParticipant(archive, userId)) {
            throw new BusinessException(ErrorCode.NOT_RESOURCE_OWNER);
        }
    }

    /** 편집 권한. 참여자여야 하고, 공동 편집이 종료되지 않았어야 한다. */
    public void checkEditable(Archive archive, Long userId) {
        checkViewable(archive, userId);
        if (!archive.isEditable()) {
            throw new BusinessException(ErrorCode.COLLABORATION_CLOSED);
        }
    }

    /** 소유자 전용 작업(삭제, 일정 연결 등). */
    public void checkOwner(Archive archive, Long userId) {
        if (!archive.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_RESOURCE_OWNER);
        }
    }

    /** 소유자 전용이면서 공동 편집이 종료되지 않았어야 하는 작업(초대, 공동 편집 종료). */
    public void checkOwnerAndEditable(Archive archive, Long userId) {
        checkOwner(archive, userId);
        if (!archive.isEditable()) {
            throw new BusinessException(ErrorCode.COLLABORATION_CLOSED);
        }
    }

    private boolean isParticipant(Archive archive, Long userId) {
        if (archive.isOwnedBy(userId)) {
            return true;
        }
        return collaboratorRepository.findByArchiveAndUser_Id(archive, userId)
                .map(ArchiveCollaborator::canEdit)
                .orElse(false);
    }
}
