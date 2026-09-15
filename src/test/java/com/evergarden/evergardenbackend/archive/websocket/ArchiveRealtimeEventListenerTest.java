package com.evergarden.evergardenbackend.archive.websocket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.archive.event.ArchiveRealtimeEvent;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 커밋 후에만 나가야 한다는 것 자체(AFTER_COMMIT)는 스프링 트랜잭션 인프라의 계약이라
 * 단위 테스트로는 재현하기 어렵다 — 그 경로는 실전 WebSocket 검증으로 이미 확인했다.
 * 여기서는 리스너가 이벤트 필드를 봉투 조립기에 정확히 그대로 넘기는지만 본다.
 */
class ArchiveRealtimeEventListenerTest {

    private final ArchiveRealtimeMessenger messenger = mock(ArchiveRealtimeMessenger.class);
    private final ArchiveRealtimeEventListener listener = new ArchiveRealtimeEventListener(messenger);

    @Test
    @DisplayName("이벤트 필드를 그대로 메신저에 위임한다")
    void 이벤트를_그대로_위임() {
        Object payload = Map.of("title", "새 제목");
        ArchiveRealtimeEvent event = new ArchiveRealtimeEvent(5L, 10L, "archive.updated", payload);

        listener.onArchiveRealtimeEvent(event);

        verify(messenger).send(5L, 10L, "archive.updated", payload);
    }
}
