package com.evergarden.evergardenbackend.archive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveCollaborator;
import com.evergarden.evergardenbackend.archive.entity.ArchiveTheme;
import com.evergarden.evergardenbackend.archive.entity.CollaborationStatus;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 공동 편집 초대·수락·거절·나가기·종료(ARCH-10·11·13·14·18). */
class ArchiveCollaborationServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long INVITEE_ID = 2L;

    private final ArchiveRepository archiveRepository = mock(ArchiveRepository.class);
    private final ArchiveItemRepository archiveItemRepository = mock(ArchiveItemRepository.class);
    private final ArchiveCollaboratorRepository collaboratorRepository = mock(ArchiveCollaboratorRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ArchiveAccessGuard accessGuard = mock(ArchiveAccessGuard.class);
    private final ArchiveMapper archiveMapper = mock(ArchiveMapper.class);
    private final com.evergarden.evergardenbackend.archive.websocket.ArchiveEditorRegistry editorRegistry =
            mock(com.evergarden.evergardenbackend.archive.websocket.ArchiveEditorRegistry.class);
    private final org.springframework.context.ApplicationEventPublisher eventPublisher =
            mock(org.springframework.context.ApplicationEventPublisher.class);

    private final ArchiveCollaborationService service = new ArchiveCollaborationService(
            archiveRepository, archiveItemRepository, collaboratorRepository,
            userRepository, accessGuard, archiveMapper, editorRegistry, eventPublisher);

    private Archive archive;
    private User invitee;

    @BeforeEach
    void setUp() {
        User owner = User.builder().nickname("주인").build();
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);
        archive = Archive.builder().owner(owner).title("t").theme(ArchiveTheme.POLAROID).build();
        ReflectionTestUtils.setField(archive, "id", 10L);
        given(archiveRepository.findById(10L)).willReturn(Optional.of(archive));

        invitee = User.builder().nickname("동행자").build();
        ReflectionTestUtils.setField(invitee, "id", INVITEE_ID);
        given(collaboratorRepository.findByArchive(archive)).willReturn(List.of());
    }

    private ArchiveCollaborator collaboratorWith(CollaboratorStatus status) {
        ArchiveCollaborator c = ArchiveCollaborator.invite(archive, invitee, LocalDateTime.now());
        if (status == CollaboratorStatus.JOINED) {
            c.accept(LocalDateTime.now());
        } else if (status == CollaboratorStatus.LEFT) {
            c.leave();
        }
        return c;
    }

    // ── invite ───────────────────────────────────────────────────

    @Test
    @DisplayName("없는 유저를 초대하면 USER_NOT_FOUND")
    void 없는_유저_초대() {
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, INVITEE_ID)).willReturn(Optional.empty());
        given(userRepository.findById(INVITEE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.invite(OWNER_ID, 10L, INVITEE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 초대한 사용자면 ALREADY_INVITED")
    void 이미_초대함() {
        given(userRepository.findById(INVITEE_ID)).willReturn(Optional.of(invitee));
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, INVITEE_ID))
                .willReturn(Optional.of(collaboratorWith(CollaboratorStatus.INVITED)));

        assertThatThrownBy(() -> service.invite(OWNER_ID, 10L, INVITEE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_INVITED);
    }

    @Test
    @DisplayName("이미 참여 중인 사용자면 ALREADY_COLLABORATOR")
    void 이미_참여중() {
        given(userRepository.findById(INVITEE_ID)).willReturn(Optional.of(invitee));
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, INVITEE_ID))
                .willReturn(Optional.of(collaboratorWith(CollaboratorStatus.JOINED)));

        assertThatThrownBy(() -> service.invite(OWNER_ID, 10L, INVITEE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_COLLABORATOR);
    }

    @Test
    @DisplayName("나갔던(LEFT) 사람은 같은 행을 재사용해 다시 초대 상태가 된다")
    void 나간_사람_재초대() {
        given(userRepository.findById(INVITEE_ID)).willReturn(Optional.of(invitee));
        ArchiveCollaborator left = collaboratorWith(CollaboratorStatus.LEFT);
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, INVITEE_ID)).willReturn(Optional.of(left));

        service.invite(OWNER_ID, 10L, INVITEE_ID);

        assertThat(left.getStatus()).isEqualTo(CollaboratorStatus.INVITED);
        assertThat(left.getJoinedAt()).isNull();
    }

    @Test
    @DisplayName("처음 초대하면 공동 편집이 OPEN으로 바뀐다")
    void 첫_초대는_OPEN으로() {
        given(userRepository.findById(INVITEE_ID)).willReturn(Optional.of(invitee));
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, INVITEE_ID)).willReturn(Optional.empty());
        given(collaboratorRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.invite(OWNER_ID, 10L, INVITEE_ID);

        assertThat(archive.getCollaborationStatus()).isEqualTo(CollaborationStatus.OPEN);
    }

    @Test
    @DisplayName("소유자 전용 검사(권한·종료 여부)는 ArchiveAccessGuard에 위임한다")
    void 초대_권한_위임() {
        doThrow(new BusinessException(ErrorCode.COLLABORATION_CLOSED))
                .when(accessGuard).checkOwnerAndEditable(archive, OWNER_ID);

        assertThatThrownBy(() -> service.invite(OWNER_ID, 10L, INVITEE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COLLABORATION_CLOSED);
        verify(userRepository, never()).findById(any());
    }

    // ── accept ───────────────────────────────────────────────────

    @Test
    @DisplayName("초대받은 적 없으면 NOT_COLLABORATOR")
    void 초대_없이_수락() {
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, INVITEE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(INVITEE_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_COLLABORATOR);
    }

    @Test
    @DisplayName("이미 수락한 초대를 또 수락하면 ALREADY_COLLABORATOR")
    void 중복_수락() {
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, INVITEE_ID))
                .willReturn(Optional.of(collaboratorWith(CollaboratorStatus.JOINED)));

        assertThatThrownBy(() -> service.accept(INVITEE_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_COLLABORATOR);
    }

    @Test
    @DisplayName("종료된 뒤에는 수락할 수 없다")
    void 종료후_수락() {
        archive.closeCollaboration();
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, INVITEE_ID))
                .willReturn(Optional.of(collaboratorWith(CollaboratorStatus.INVITED)));

        assertThatThrownBy(() -> service.accept(INVITEE_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COLLABORATION_CLOSED);
    }

    @Test
    @DisplayName("정상 수락은 JOINED가 된다")
    void 정상_수락() {
        ArchiveCollaborator invited = collaboratorWith(CollaboratorStatus.INVITED);
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, INVITEE_ID)).willReturn(Optional.of(invited));

        service.accept(INVITEE_ID, 10L);

        assertThat(invited.getStatus()).isEqualTo(CollaboratorStatus.JOINED);
    }

    // ── decline ──────────────────────────────────────────────────

    @Test
    @DisplayName("초대받은 적 없으면 거절도 NOT_COLLABORATOR")
    void 초대_없이_거절() {
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, INVITEE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.decline(INVITEE_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_COLLABORATOR);
    }

    @Test
    @DisplayName("이미 수락한 초대는 거절할 수 없다 — 나가기를 써야 한다")
    void 수락한_초대는_거절불가() {
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, INVITEE_ID))
                .willReturn(Optional.of(collaboratorWith(CollaboratorStatus.JOINED)));

        assertThatThrownBy(() -> service.decline(INVITEE_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_COLLABORATOR);
    }

    @Test
    @DisplayName("정상 거절은 행을 지운다 — 나중에 다시 초대받을 수 있어야 한다")
    void 정상_거절() {
        ArchiveCollaborator invited = collaboratorWith(CollaboratorStatus.INVITED);
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, INVITEE_ID)).willReturn(Optional.of(invited));

        service.decline(INVITEE_ID, 10L);

        verify(collaboratorRepository).delete(invited);
    }

    // ── leave ────────────────────────────────────────────────────

    @Test
    @DisplayName("소유자는 나갈 수 없다")
    void 소유자는_나갈수없음() {
        assertThatThrownBy(() -> service.leave(OWNER_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("참여 중이 아니면 NOT_COLLABORATOR")
    void 참여중_아니면_나가기_거절() {
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, INVITEE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.leave(INVITEE_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_COLLABORATOR);
    }

    @Test
    @DisplayName("정상 나가기는 LEFT가 된다")
    void 정상_나가기() {
        ArchiveCollaborator joined = collaboratorWith(CollaboratorStatus.JOINED);
        given(collaboratorRepository.findByArchiveAndUser_Id(archive, INVITEE_ID)).willReturn(Optional.of(joined));

        service.leave(INVITEE_ID, 10L);

        assertThat(joined.getStatus()).isEqualTo(CollaboratorStatus.LEFT);
    }

    // ── close ────────────────────────────────────────────────────

    @Test
    @DisplayName("종료는 소유자 전용 검사를 거치고 CLOSED로 바뀐다")
    void 정상_종료() {
        service.close(OWNER_ID, 10L);

        verify(accessGuard).checkOwnerAndEditable(archive, OWNER_ID);
        assertThat(archive.getCollaborationStatus()).isEqualTo(CollaborationStatus.CLOSED);
    }

    @Test
    @DisplayName("종료하면 collaboration.closed 이벤트를 발행한다")
    void 종료_실시간_이벤트() {
        service.close(OWNER_ID, 10L);

        org.mockito.ArgumentCaptor<com.evergarden.evergardenbackend.archive.event.ArchiveRealtimeEvent> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.evergarden.evergardenbackend.archive.event.ArchiveRealtimeEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("collaboration.closed");
        assertThat(captor.getValue().payload()).isNull();
    }

    // ── getSession (ARCH-12) ─────────────────────────────────────

    @Test
    @DisplayName("편집 권한이 없으면 세션 발급도 거절된다")
    void 세션_권한_위임() {
        doThrow(new BusinessException(ErrorCode.COLLABORATION_CLOSED))
                .when(accessGuard).checkEditable(archive, OWNER_ID);

        assertThatThrownBy(() -> service.getSession(OWNER_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COLLABORATION_CLOSED);
    }

    @Test
    @DisplayName("접속 정보에는 지금 접속해 있는 참여자만 담긴다")
    void 세션_활성편집자만_포함() {
        ReflectionTestUtils.setField(service, "websocketUrl", "ws://test/ws");
        ArchiveCollaborator joined = collaboratorWith(CollaboratorStatus.JOINED);
        given(collaboratorRepository.findByArchive(archive)).willReturn(List.of(joined));
        given(editorRegistry.activeUserIds(10L)).willReturn(List.of(INVITEE_ID));

        var session = service.getSession(OWNER_ID, 10L);

        assertThat(session.websocketUrl()).isEqualTo("ws://test/ws");
        assertThat(session.topic()).isEqualTo("/topic/archives/10");
        assertThat(session.activeEditors()).hasSize(1);
        assertThat(session.activeEditors().get(0).userId()).isEqualTo(INVITEE_ID);
    }
}
