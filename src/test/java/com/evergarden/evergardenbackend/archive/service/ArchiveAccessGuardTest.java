package com.evergarden.evergardenbackend.archive.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveCollaborator;
import com.evergarden.evergardenbackend.archive.entity.ArchiveTheme;
import com.evergarden.evergardenbackend.archive.entity.CollaboratorStatus;
import com.evergarden.evergardenbackend.archive.repository.ArchiveCollaboratorRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.user.entity.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 소유자·공동편집자 판별 — 21개 오퍼레이션이 이 세 메서드에 기댄다. */
class ArchiveAccessGuardTest {

    private static final Long OWNER_ID = 1L;
    private static final Long EDITOR_ID = 2L;
    private static final Long STRANGER_ID = 3L;

    private final ArchiveCollaboratorRepository collaboratorRepository = mock(ArchiveCollaboratorRepository.class);
    private final ArchiveAccessGuard guard = new ArchiveAccessGuard(collaboratorRepository);

    private Archive archive() {
        User owner = User.builder().nickname("소유자").build();
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);
        Archive archive = Archive.builder().owner(owner).title("t").theme(ArchiveTheme.POLAROID).build();
        ReflectionTestUtils.setField(archive, "id", 10L);
        return archive;
    }

    private ArchiveCollaborator collaborator(Archive archive, CollaboratorStatus status) {
        ArchiveCollaborator c = ArchiveCollaborator.invite(archive, mock(User.class), LocalDateTime.now());
        if (status == CollaboratorStatus.JOINED) {
            c.accept(LocalDateTime.now());
        } else if (status == CollaboratorStatus.LEFT) {
            c.leave();
        }
        return c;
    }

    @Test
    @DisplayName("소유자는 조회·편집·소유자 전용 작업을 모두 통과한다")
    void 소유자는_전부_통과() {
        Archive archive = archive();

        assertThatCode(() -> guard.checkViewable(archive, OWNER_ID)).doesNotThrowAnyException();
        assertThatCode(() -> guard.checkEditable(archive, OWNER_ID)).doesNotThrowAnyException();
        assertThatCode(() -> guard.checkOwner(archive, OWNER_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("참여(JOINED) 중인 공동편집자는 조회·편집은 되지만 소유자 전용은 막힌다")
    void 참여자는_조회_편집만_된다() {
        Archive archive = archive();
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, EDITOR_ID))
                .willReturn(Optional.of(collaborator(archive, CollaboratorStatus.JOINED)));

        assertThatCode(() -> guard.checkViewable(archive, EDITOR_ID)).doesNotThrowAnyException();
        assertThatCode(() -> guard.checkEditable(archive, EDITOR_ID)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.checkOwner(archive, EDITOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("초대만 받고 아직 수락 안 한 사람은 참여자가 아니다")
    void 초대만_받은_사람은_거절() {
        Archive archive = archive();
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, EDITOR_ID))
                .willReturn(Optional.of(collaborator(archive, CollaboratorStatus.INVITED)));

        assertThatThrownBy(() -> guard.checkViewable(archive, EDITOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("나간(LEFT) 사람은 더 이상 조회할 수 없다")
    void 나간_사람은_거절() {
        Archive archive = archive();
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, EDITOR_ID))
                .willReturn(Optional.of(collaborator(archive, CollaboratorStatus.LEFT)));

        assertThatThrownBy(() -> guard.checkViewable(archive, EDITOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("아무 관계 없는 사람은 NOT_RESOURCE_OWNER")
    void 관계없는_사람은_거절() {
        Archive archive = archive();
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, STRANGER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> guard.checkViewable(archive, STRANGER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("공동 편집이 종료되면 소유자도 편집은 못 하지만 조회는 된다(ADR-017)")
    void 종료되면_조회는_되고_편집만_막힌다() {
        Archive archive = archive();
        archive.closeCollaboration();

        assertThatCode(() -> guard.checkViewable(archive, OWNER_ID)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.checkEditable(archive, OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COLLABORATION_CLOSED);
    }

    @Test
    @DisplayName("checkOwnerAndEditable — 소유자가 아니면 NOT_RESOURCE_OWNER, 종료됐으면 COLLABORATION_CLOSED")
    void 소유자전용_편집가능_검사() {
        Archive archive = archive();
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, EDITOR_ID))
                .willReturn(Optional.of(collaborator(archive, CollaboratorStatus.JOINED)));

        assertThatThrownBy(() -> guard.checkOwnerAndEditable(archive, EDITOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_RESOURCE_OWNER);

        archive.closeCollaboration();
        assertThatThrownBy(() -> guard.checkOwnerAndEditable(archive, OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COLLABORATION_CLOSED);
    }
}
