package com.evergarden.evergardenbackend.archive.websocket;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 지금 접속해 있는 편집자를 메모리에 들고 있는다(ADR-051).
 *
 * <p>인스턴스 하나짜리 배포를 전제로 한다 — 서버를 여러 대로 늘리면 이 상태를
 * Redis Pub/Sub 같은 공유 저장소로 옮겨야 한다({@code docs/realtime-editing.md} 9절).
 *
 * <p>한 사람이 두 기기로 접속해도 목록엔 한 번만 보여야 하고, {@code editor.left}는
 * 마지막 연결이 끊길 때만 나가야 한다 — 그래서 세션 하나가 아니라
 * "아카이브+유저당 세션 집합"을 기준으로 입장·퇴장을 판단한다.
 */
@Component
public class ArchiveEditorRegistry {

    /** {@code editor.left} 발행 대상. 어느 아카이브에서 누가 나갔는지 담는다. */
    public record LeftEditor(Long archiveId, Long userId) {
    }

    private record Session(Long archiveId, Long userId) {
    }

    private final Map<String, Session> sessionsById = new ConcurrentHashMap<>();
    private final Map<Long, Map<Long, Set<String>>> sessionIdsByArchiveAndUser = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastPingAt = new ConcurrentHashMap<>();

    /** 아카이브 토픽을 구독했다. 그 사람의 첫 연결이면 {@code true}({@code editor.joined} 발행 대상). */
    public boolean subscribe(String sessionId, Long archiveId, Long userId) {
        sessionsById.put(sessionId, new Session(archiveId, userId));
        lastPingAt.put(sessionId, Instant.now());
        Set<String> sessions = sessionIdsByArchiveAndUser
                .computeIfAbsent(archiveId, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet());
        boolean firstConnection = sessions.isEmpty();
        sessions.add(sessionId);
        return firstConnection;
    }

    public void ping(String sessionId) {
        lastPingAt.computeIfPresent(sessionId, (id, previous) -> Instant.now());
    }

    /** 세션이 끊겼다. 그 사람의 마지막 연결이었으면 값을 돌려준다({@code editor.left} 발행 대상). */
    public Optional<LeftEditor> disconnect(String sessionId) {
        Session session = sessionsById.remove(sessionId);
        lastPingAt.remove(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        Map<Long, Set<String>> byUser = sessionIdsByArchiveAndUser.get(session.archiveId());
        if (byUser == null) {
            return Optional.empty();
        }
        Set<String> sessions = byUser.get(session.userId());
        if (sessions == null || !sessions.remove(sessionId) || !sessions.isEmpty()) {
            return Optional.empty();
        }
        byUser.remove(session.userId());
        return Optional.of(new LeftEditor(session.archiveId(), session.userId()));
    }

    public List<Long> activeUserIds(Long archiveId) {
        Map<Long, Set<String>> byUser = sessionIdsByArchiveAndUser.get(archiveId);
        return byUser == null ? List.of() : List.copyOf(byUser.keySet());
    }

    /** {@code timeout} 동안 ping이 없던 세션. 60초 무응답이면 죽은 연결로 본다(문서 4절). */
    public List<String> staleSessions(Duration timeout) {
        Instant threshold = Instant.now().minus(timeout);
        return lastPingAt.entrySet().stream()
                .filter(entry -> entry.getValue().isBefore(threshold))
                .map(Map.Entry::getKey)
                .toList();
    }
}
