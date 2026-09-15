package com.evergarden.evergardenbackend.archive.websocket;

import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 아카이브 토픽으로 실시간 알림을 실제로 내보내는 곳. 두 경로가 이걸 함께 쓴다 —
 *
 * <ul>
 *   <li>HTTP 오퍼레이션이 저장한 뒤 {@code ArchiveRealtimeEventListener}를 거쳐 부르는 경로
 *       (item.added 등, 트랜잭션 커밋 후)</li>
 *   <li>STOMP 세션 구독·해제를 직접 감지해 부르는 경로(editor.joined·editor.left) —
 *       이쪽은 애초에 DB 트랜잭션 안이 아니라서 이벤트 리스너를 거치지 않는다</li>
 * </ul>
 *
 * <p>봉투 모양은 {@code docs/realtime-editing.md} 3절과 같다 —
 * {@code {type, archiveId, actor, occurredAt, payload}}.
 */
@Component
@RequiredArgsConstructor
public class ArchiveRealtimeMessenger {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    public void send(Long archiveId, Long actorUserId, String type, Object payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type);
        envelope.put("archiveId", archiveId);
        envelope.put("actor", actorOf(actorUserId));
        envelope.put("occurredAt", LocalDateTime.now());
        envelope.put("payload", payload);
        // convertAndSend(D, Object)와 convertAndSend(Object, Map<String,Object>) 오버로드가
        // Map을 payload로 줄 때 서로 겹쳐서, payload 쪽인지 명시해야 한다
        messagingTemplate.convertAndSend("/topic/archives/" + archiveId, (Object) envelope);
    }

    private Map<String, Object> actorOf(Long userId) {
        return userRepository.findById(userId)
                .<Map<String, Object>>map(this::toActor)
                .orElse(null);
    }

    private Map<String, Object> toActor(User user) {
        return Map.of("userId", user.getId(), "nickname", user.getNickname());
    }
}
