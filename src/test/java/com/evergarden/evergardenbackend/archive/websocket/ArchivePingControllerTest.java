package com.evergarden.evergardenbackend.archive.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

/**
 * ping으로 쫓겨난 세션이 복구되는지 — {@code registry.subscribe()}를 재사용해서,
 * 60초 타임아웃 뒤 늦게 도착한 ping도 {@code editor.joined}를 다시 쏠 수 있어야 한다.
 */
class ArchivePingControllerTest {

    private static final Long ARCHIVE_ID = 5L;
    private static final Long USER_ID = 10L;
    private static final String SESSION_ID = "session-1";

    private final ArchiveEditorRegistry registry = mock(ArchiveEditorRegistry.class);
    private final ArchiveEditorPresenceListener presenceListener = mock(ArchiveEditorPresenceListener.class);
    private final ArchivePingController controller = new ArchivePingController(registry, presenceListener);

    private SimpMessageHeaderAccessor accessor() {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionId(SESSION_ID);
        return accessor;
    }

    @Test
    @DisplayName("정상 ping이면 subscribe로 생존 시각을 갱신하고, 첫 연결이 아니므로 editor.joined는 안 쏜다")
    void 정상_ping이면_subscribe_호출() {
        SimpMessageHeaderAccessor accessor = accessor();
        given(presenceListener.userIdOf(accessor)).willReturn(USER_ID);
        given(registry.subscribe(SESSION_ID, ARCHIVE_ID, USER_ID)).willReturn(false);

        controller.ping(ARCHIVE_ID, accessor);

        verify(registry).subscribe(SESSION_ID, ARCHIVE_ID, USER_ID);
        verify(presenceListener).notifyJoinedIfFirst(ARCHIVE_ID, USER_ID, false);
    }

    @Test
    @DisplayName("60초 타임아웃으로 쫓겨난 뒤의 늦은 ping이면 복구되고 editor.joined를 다시 쏜다")
    void 쫓겨난_뒤_ping이면_editor_joined_재발행() {
        SimpMessageHeaderAccessor accessor = accessor();
        given(presenceListener.userIdOf(accessor)).willReturn(USER_ID);
        // registry 입장에서 세션이 사라진 상태라, subscribe가 "첫 연결"(true)로 복구시킴
        given(registry.subscribe(SESSION_ID, ARCHIVE_ID, USER_ID)).willReturn(true);

        controller.ping(ARCHIVE_ID, accessor);

        verify(presenceListener).notifyJoinedIfFirst(ARCHIVE_ID, USER_ID, true);
    }

    @Test
    @DisplayName("user를 식별할 수 없으면 아무것도 하지 않는다")
    void user_없으면_무시() {
        SimpMessageHeaderAccessor accessor = accessor();
        given(presenceListener.userIdOf(accessor)).willReturn(null);

        controller.ping(ARCHIVE_ID, accessor);

        verifyNoInteractions(registry);
        verify(presenceListener, never()).notifyJoinedIfFirst(any(), any(), anyBoolean());
    }
}
