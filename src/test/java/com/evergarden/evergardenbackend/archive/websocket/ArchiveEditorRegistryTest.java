package com.evergarden.evergardenbackend.archive.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.evergarden.evergardenbackend.archive.websocket.ArchiveEditorRegistry.LeftEditor;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 접속 중인 편집자 집계 — editor.joined·editor.left 발행 여부를 여기서 판단한다. */
class ArchiveEditorRegistryTest {

    private static final Long ARCHIVE_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;

    private final ArchiveEditorRegistry registry = new ArchiveEditorRegistry();

    @Test
    @DisplayName("한 사람의 첫 연결이면 true를 돌려준다")
    void 첫_연결이면_true() {
        boolean firstConnection = registry.subscribe("session-1", ARCHIVE_ID, USER_ID);

        assertThat(firstConnection).isTrue();
    }

    @Test
    @DisplayName("같은 사람이 두 번째 기기로 접속해도 첫 연결이 아니다")
    void 두번째_세션은_첫_연결이_아님() {
        registry.subscribe("session-1", ARCHIVE_ID, USER_ID);
        boolean secondConnection = registry.subscribe("session-2", ARCHIVE_ID, USER_ID);

        assertThat(secondConnection).isFalse();
    }

    @Test
    @DisplayName("다른 아카이브·다른 사람의 접속은 서로 영향을 주지 않는다")
    void 다른_아카이브와_사람은_독립적() {
        registry.subscribe("session-1", ARCHIVE_ID, USER_ID);

        boolean otherUserFirst = registry.subscribe("session-2", ARCHIVE_ID, OTHER_USER_ID);
        boolean otherArchiveFirst = registry.subscribe("session-3", 2L, USER_ID);

        assertThat(otherUserFirst).isTrue();
        assertThat(otherArchiveFirst).isTrue();
    }

    @Test
    @DisplayName("마지막 연결이 아니면 disconnect는 빈 값을 돌려준다")
    void 마지막_연결이_아니면_빈값() {
        registry.subscribe("session-1", ARCHIVE_ID, USER_ID);
        registry.subscribe("session-2", ARCHIVE_ID, USER_ID);

        Optional<LeftEditor> left = registry.disconnect("session-1");

        assertThat(left).isEmpty();
    }

    @Test
    @DisplayName("마지막 연결이 끊기면 editor.left 대상을 돌려준다")
    void 마지막_연결이_끊기면_LeftEditor() {
        registry.subscribe("session-1", ARCHIVE_ID, USER_ID);
        registry.subscribe("session-2", ARCHIVE_ID, USER_ID);
        registry.disconnect("session-1");

        Optional<LeftEditor> left = registry.disconnect("session-2");

        assertThat(left).contains(new LeftEditor(ARCHIVE_ID, USER_ID));
    }

    @Test
    @DisplayName("모르는 세션을 끊어도 예외 없이 빈 값을 돌려준다")
    void 모르는_세션은_빈값() {
        Optional<LeftEditor> left = registry.disconnect("no-such-session");

        assertThat(left).isEmpty();
    }

    @Test
    @DisplayName("마지막 연결이 끊긴 뒤 같은 사람이 다시 접속하면 또 첫 연결이다")
    void 완전히_나갔다_다시_들어오면_다시_첫_연결() {
        registry.subscribe("session-1", ARCHIVE_ID, USER_ID);
        registry.disconnect("session-1");

        boolean firstConnectionAgain = registry.subscribe("session-2", ARCHIVE_ID, USER_ID);

        assertThat(firstConnectionAgain).isTrue();
    }

    @Test
    @DisplayName("접속 중인 사람 목록에는 각자 한 번씩만 나온다")
    void 활성_유저_목록_중복없음() {
        registry.subscribe("session-1", ARCHIVE_ID, USER_ID);
        registry.subscribe("session-2", ARCHIVE_ID, USER_ID);
        registry.subscribe("session-3", ARCHIVE_ID, OTHER_USER_ID);

        assertThat(registry.activeUserIds(ARCHIVE_ID)).containsExactlyInAnyOrder(USER_ID, OTHER_USER_ID);
    }

    @Test
    @DisplayName("모두 나가면 접속 중인 사람 목록이 빈다")
    void 모두_나가면_목록_빔() {
        registry.subscribe("session-1", ARCHIVE_ID, USER_ID);
        registry.disconnect("session-1");

        assertThat(registry.activeUserIds(ARCHIVE_ID)).isEmpty();
    }

    @Test
    @DisplayName("ping이 없던 세션만 죽은 연결로 잡는다")
    void ping_없는_세션만_stale() throws InterruptedException {
        registry.subscribe("session-1", ARCHIVE_ID, USER_ID);
        registry.subscribe("session-2", ARCHIVE_ID, OTHER_USER_ID);
        Thread.sleep(50);
        registry.ping("session-2");

        var stale = registry.staleSessions(Duration.ofMillis(20));

        assertThat(stale).containsExactly("session-1");
    }
}
