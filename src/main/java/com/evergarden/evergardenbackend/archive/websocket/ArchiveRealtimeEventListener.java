package com.evergarden.evergardenbackend.archive.websocket;

import com.evergarden.evergardenbackend.archive.event.ArchiveRealtimeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * HTTP 오퍼레이션이 발행한 이벤트를 커밋 후에만 STOMP로 내보낸다.
 *
 * <p>서비스 계층에서 곧바로 발행하면 트랜잭션이 커밋되기 전에 알림이 나갈 수 있다 —
 * 다른 참여자가 아직 DB에 없는 변경을 클라이언트에서 먼저 보게 된다({@code docs/realtime-editing.md} 9절).
 */
@Component
@RequiredArgsConstructor
public class ArchiveRealtimeEventListener {

    private final ArchiveRealtimeMessenger messenger;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onArchiveRealtimeEvent(ArchiveRealtimeEvent event) {
        messenger.send(event.archiveId(), event.actorUserId(), event.type(), event.payload());
    }
}
