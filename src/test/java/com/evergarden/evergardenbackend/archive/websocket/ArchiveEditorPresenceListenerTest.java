package com.evergarden.evergardenbackend.archive.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.evergarden.evergardenbackend.archive.dto.CollaboratorResponse;
import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveCollaborator;
import com.evergarden.evergardenbackend.archive.entity.ArchiveTheme;
import com.evergarden.evergardenbackend.archive.repository.ArchiveCollaboratorRepository;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.global.security.StompPrincipal;
import com.evergarden.evergardenbackend.user.entity.User;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/**
 * editor.joined·editor.left 발행 여부 — 세션의 {@code accessor.getUser()}가 실제로는
 * {@link StompPrincipal}이 아닐 수 있다는 것(핸드셰이크 시점 principal이 대신 쓰이는 등)을
 * 실전 검증에서 확인했던 곳이라, 이 판별 로직을 회귀 없이 잠가둔다.
 */
class ArchiveEditorPresenceListenerTest {

    private static final Long ARCHIVE_ID = 5L;
    private static final Long USER_ID = 10L;

    private final ArchiveEditorRegistry registry = mock(ArchiveEditorRegistry.class);
    private final ArchiveRealtimeMessenger messenger = mock(ArchiveRealtimeMessenger.class);
    private final ArchiveCollaboratorRepository collaboratorRepository = mock(ArchiveCollaboratorRepository.class);
    private final ArchiveEditorPresenceListener listener =
            new ArchiveEditorPresenceListener(registry, messenger, collaboratorRepository);

    private SessionSubscribeEvent subscribeEvent(String destination, String sessionId, Object user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (user instanceof java.security.Principal principal) {
            accessor.setUser(principal);
        }
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionSubscribeEvent(this, message);
    }

    private SessionDisconnectEvent disconnectEvent(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionDisconnectEvent(this, message, sessionId, CloseStatus.NORMAL);
    }

    private StompPrincipal principal(Long userId) {
        return new StompPrincipal(new AuthPrincipal(userId, Role.USER));
    }

    @Test
    @DisplayName("첫 구독 + 참여자면 editor.joined를 발행한다")
    void 첫_구독이면_editor_joined() {
        given(registry.subscribe("session-1", ARCHIVE_ID, USER_ID)).willReturn(true);
        User user = User.builder().nickname("편집자").build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        Archive archive = Archive.builder().owner(user).title("t").theme(ArchiveTheme.POLAROID).build();
        ArchiveCollaborator collaborator = ArchiveCollaborator.owner(archive, user, LocalDateTime.now());
        given(collaboratorRepository.findByArchive_IdAndUser_IdWithUser(ARCHIVE_ID, USER_ID))
                .willReturn(Optional.of(collaborator));

        listener.onSubscribe(subscribeEvent("/topic/archives/5", "session-1", principal(USER_ID)));

        ArgumentCaptor<CollaboratorResponse> payload = ArgumentCaptor.forClass(CollaboratorResponse.class);
        verify(messenger).send(eq(ARCHIVE_ID), eq(USER_ID), eq("editor.joined"), payload.capture());
        assertThat(payload.getValue().userId()).isEqualTo(USER_ID);
        assertThat(payload.getValue().nickname()).isEqualTo("편집자");
    }

    @Test
    @DisplayName("이미 접속 중이던 사람의 추가 세션이면 editor.joined를 또 보내지 않는다")
    void 첫_구독이_아니면_발행안함() {
        given(registry.subscribe("session-2", ARCHIVE_ID, USER_ID)).willReturn(false);

        listener.onSubscribe(subscribeEvent("/topic/archives/5", "session-2", principal(USER_ID)));

        verify(messenger, never()).send(anyLong(), anyLong(), anyString(), any());
        verifyNoInteractions(collaboratorRepository);
    }

    @Test
    @DisplayName("첫 구독이어도 공동편집자 명단에 없으면 editor.joined를 보내지 않는다")
    void 참여자가_아니면_발행안함() {
        given(registry.subscribe("session-1", ARCHIVE_ID, USER_ID)).willReturn(true);
        given(collaboratorRepository.findByArchive_IdAndUser_IdWithUser(ARCHIVE_ID, USER_ID))
                .willReturn(Optional.empty());

        listener.onSubscribe(subscribeEvent("/topic/archives/5", "session-1", principal(USER_ID)));

        verify(messenger, never()).send(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("아카이브 토픽이 아니면 무시한다")
    void 아카이브_토픽이_아니면_무시() {
        listener.onSubscribe(subscribeEvent("/topic/other/5", "session-1", principal(USER_ID)));

        verifyNoInteractions(registry, messenger, collaboratorRepository);
    }

    @Test
    @DisplayName("목적지가 없으면 무시한다")
    void 목적지_없으면_무시() {
        listener.onSubscribe(subscribeEvent(null, "session-1", principal(USER_ID)));

        verifyNoInteractions(registry, messenger, collaboratorRepository);
    }

    @Test
    @DisplayName("세션의 user가 StompPrincipal이 아니면 무시한다 — 핸드셰이크 시점 principal이 잘못 쓰였던 실전 버그 회귀 방지")
    void StompPrincipal이_아니면_무시() {
        var otherPrincipal = (java.security.Principal) () -> "1";

        listener.onSubscribe(subscribeEvent("/topic/archives/5", "session-1", otherPrincipal));

        verifyNoInteractions(registry, messenger, collaboratorRepository);
    }

    @Test
    @DisplayName("user가 아예 없으면 무시한다")
    void user_없으면_무시() {
        listener.onSubscribe(subscribeEvent("/topic/archives/5", "session-1", null));

        verifyNoInteractions(registry, messenger, collaboratorRepository);
    }

    @Test
    @DisplayName("마지막 세션이 끊기면 editor.left를 발행한다")
    void 마지막_연결해제면_editor_left() {
        given(registry.disconnect("session-1"))
                .willReturn(Optional.of(new ArchiveEditorRegistry.LeftEditor(ARCHIVE_ID, USER_ID)));

        listener.onDisconnect(disconnectEvent("session-1"));

        verify(messenger).send(ARCHIVE_ID, USER_ID, "editor.left", Map.of("userId", USER_ID));
    }

    @Test
    @DisplayName("마지막 세션이 아니면 editor.left를 보내지 않는다")
    void 마지막이_아니면_발행안함() {
        given(registry.disconnect("session-1")).willReturn(Optional.empty());

        listener.onDisconnect(disconnectEvent("session-1"));

        verify(messenger, never()).send(anyLong(), anyLong(), anyString(), any());
    }
}
