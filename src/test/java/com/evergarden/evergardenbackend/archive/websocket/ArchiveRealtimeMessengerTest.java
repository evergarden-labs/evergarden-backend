package com.evergarden.evergardenbackend.archive.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/** 실시간 봉투 조립 — {@code docs/realtime-editing.md} 3절의 {type, archiveId, actor, occurredAt, payload} 모양. */
class ArchiveRealtimeMessengerTest {

    private static final Long ARCHIVE_ID = 5L;
    private static final Long USER_ID = 10L;

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ArchiveRealtimeMessenger messenger =
            new ArchiveRealtimeMessenger(messagingTemplate, userRepository);

    private User user(String nickname) {
        User user = User.builder().nickname(nickname).build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    @Test
    @DisplayName("아카이브 토픽으로 봉투를 조립해 보낸다")
    void 봉투_조립() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("보내는사람")));

        messenger.send(ARCHIVE_ID, USER_ID, "archive.updated", Map.of("title", "새 제목"));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/archives/5"), payloadCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(envelope.get("type")).isEqualTo("archive.updated");
        assertThat(envelope.get("archiveId")).isEqualTo(ARCHIVE_ID);
        assertThat(envelope.get("occurredAt")).isInstanceOf(LocalDateTime.class);
        assertThat(envelope.get("payload")).isEqualTo(Map.of("title", "새 제목"));

        @SuppressWarnings("unchecked")
        Map<String, Object> actor = (Map<String, Object>) envelope.get("actor");
        assertThat(actor.get("userId")).isEqualTo(USER_ID);
        assertThat(actor.get("nickname")).isEqualTo("보내는사람");
    }

    @Test
    @DisplayName("작성자가 이미 탈퇴 등으로 없으면 actor가 null이어도 나머지는 그대로 나간다")
    void 작성자_없으면_actor_null() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        messenger.send(ARCHIVE_ID, USER_ID, "editor.left", Map.of("userId", USER_ID));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/archives/5"), payloadCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(envelope.get("actor")).isNull();
        assertThat(envelope.get("type")).isEqualTo("editor.left");
    }

    @Test
    @DisplayName("토픽 경로는 아카이브 id마다 다르다")
    void 아카이브별_토픽_분리() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("보내는사람")));

        messenger.send(999L, USER_ID, "archive.updated", Map.of());

        verify(messagingTemplate).convertAndSend(eq("/topic/archives/999"), org.mockito.ArgumentMatchers.any(Object.class));
    }
}
