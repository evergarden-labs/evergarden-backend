package com.evergarden.evergardenbackend.archive.websocket;

import com.evergarden.evergardenbackend.archive.dto.CollaboratorResponse;
import com.evergarden.evergardenbackend.archive.repository.ArchiveCollaboratorRepository;
import com.evergarden.evergardenbackend.global.security.StompPrincipal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/**
 * STOMP 구독·연결 해제를 감지해 {@code editor.joined}·{@code editor.left}를 발행한다.
 *
 * <p>DB 트랜잭션 밖에서 일어나는 일이라 {@link ArchiveRealtimeMessenger}를 직접 부른다 —
 * {@code ArchiveRealtimeEventListener}(커밋 후 발행)를 거치지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ArchiveEditorPresenceListener {

    private static final Pattern ARCHIVE_TOPIC = Pattern.compile("^/topic/archives/(\\d+)$");

    private final ArchiveEditorRegistry registry;
    private final ArchiveRealtimeMessenger messenger;
    private final ArchiveCollaboratorRepository collaboratorRepository;

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long archiveId = archiveIdOf(accessor.getDestination());
        Long userId = userIdOf(accessor);
        if (archiveId == null || userId == null) {
            return;
        }

        boolean firstConnection = registry.subscribe(accessor.getSessionId(), archiveId, userId);
        notifyJoinedIfFirst(archiveId, userId, firstConnection);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        registry.disconnect(accessor.getSessionId()).ifPresent(left ->
                messenger.send(left.archiveId(), left.userId(), "editor.left", Map.of("userId", left.userId())));
    }

    /**
     * {@code registry.subscribe()}가 "첫 연결"(true)이라고 하면 {@code editor.joined}를 쏜다.
     * 늦은 ping으로 끊긴 세션이 복구될 때도 똑같이 적용해야 해서 {@link ArchivePingController}와
     * 공유한다.
     */
    void notifyJoinedIfFirst(Long archiveId, Long userId, boolean firstConnection) {
        if (!firstConnection) {
            return;
        }
        collaboratorRepository.findByArchive_IdAndUser_IdWithUser(archiveId, userId)
                .map(CollaboratorResponse::of)
                .ifPresent(payload -> messenger.send(archiveId, userId, "editor.joined", payload));
    }

    private Long archiveIdOf(String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = ARCHIVE_TOPIC.matcher(destination);
        return matcher.matches() ? Long.valueOf(matcher.group(1)) : null;
    }

    /** {@code ArchivePingController}도 쓴다 — ping 메시지와 구독 이벤트 둘 다 이 타입으로 받을 수 있다. */
    Long userIdOf(SimpMessageHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof StompPrincipal principal)) {
            return null;
        }
        return principal.authPrincipal().userId();
    }
}
