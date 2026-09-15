package com.evergarden.evergardenbackend.archive.websocket;

import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** ping이 60초 넘게 없는 세션을 끊긴 것으로 보고 {@code editor.left}를 발행한다(문서 4절). */
@Component
@RequiredArgsConstructor
public class EditorPresenceScheduler {

    private static final Duration PING_TIMEOUT = Duration.ofSeconds(60);

    private final ArchiveEditorRegistry registry;
    private final ArchiveRealtimeMessenger messenger;

    @Scheduled(fixedRate = 15_000)
    public void evictStaleEditors() {
        for (String sessionId : registry.staleSessions(PING_TIMEOUT)) {
            registry.disconnect(sessionId).ifPresent(left ->
                    messenger.send(left.archiveId(), left.userId(), "editor.left", Map.of("userId", left.userId())));
        }
    }
}
