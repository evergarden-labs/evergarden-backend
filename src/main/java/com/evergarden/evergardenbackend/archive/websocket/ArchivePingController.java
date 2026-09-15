package com.evergarden.evergardenbackend.archive.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * 클라이언트가 30초마다 보내는 연결 유지 신호(문서 4절). 저장은 안 하고 마지막으로
 * 살아있던 시각만 갱신한다 — 60초 넘게 없으면 {@link EditorPresenceScheduler}가 끊긴 것으로 본다.
 */
@Controller
@RequiredArgsConstructor
public class ArchivePingController {

    private final ArchiveEditorRegistry registry;

    @MessageMapping("/archives/{archiveId}/ping")
    public void ping(SimpMessageHeaderAccessor headerAccessor) {
        registry.ping(headerAccessor.getSessionId());
    }
}
