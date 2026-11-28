package com.evergarden.evergardenbackend.archive.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * 클라이언트가 30초마다 보내는 연결 유지 신호(문서 4절). 60초 넘게 없으면
 * {@link EditorPresenceScheduler}가 끊긴 것으로 본다.
 *
 * <p>{@link ArchiveEditorRegistry#subscribe}를 그대로 쓴다 — 앱이 백그라운드로
 * 갔다가 60초를 넘겨 돌아와도(WebSocket 연결 자체는 안 끊긴 채로) ping 하나로
 * 다시 등록되고, 쫓겨나 있던 상태였다면 {@code editor.joined}도 다시 나간다.
 */
@Controller
@RequiredArgsConstructor
public class ArchivePingController {

    private final ArchiveEditorRegistry registry;
    private final ArchiveEditorPresenceListener presenceListener;

    @MessageMapping("/archives/{archiveId}/ping")
    public void ping(@DestinationVariable Long archiveId, SimpMessageHeaderAccessor headerAccessor) {
        Long userId = presenceListener.userIdOf(headerAccessor);
        if (userId == null) {
            return;
        }
        boolean firstConnection = registry.subscribe(headerAccessor.getSessionId(), archiveId, userId);
        presenceListener.notifyJoinedIfFirst(archiveId, userId, firstConnection);
    }
}
