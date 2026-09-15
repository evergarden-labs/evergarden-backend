package com.evergarden.evergardenbackend.archive.websocket;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveTheme;
import com.evergarden.evergardenbackend.archive.repository.ArchiveCollaboratorRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.archive.service.ArchiveAccessGuard;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.global.security.StompPrincipal;
import com.evergarden.evergardenbackend.user.entity.User;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 참여자가 아니면 아카이브 토픽 구독 자체를 막는다 — 실전 검토에서 발견된
 * "인증만 되면 아무 아카이브나 구독해서 실시간 이벤트를 엿볼 수 있는" 정보 노출을 막는 곳이라
 * 회귀 없이 잠가둔다.
 */
class ArchiveSubscriptionAuthInterceptorTest {

    private static final Long ARCHIVE_ID = 5L;
    private static final Long OWNER_ID = 1L;
    private static final Long STRANGER_ID = 99L;

    // 진짜 판별 규칙(누가 볼 수 있는가)까지 같이 검증하려고 목 대신 실물을 쓴다 —
    // ArchiveAccessGuardTest가 이미 그 규칙 자체는 촘촘히 보고 있으니 여기서는
    // "인터셉터가 그 규칙을 실제로 호출하는가"에 집중한다.
    private final ArchiveCollaboratorRepository collaboratorRepository = mock(ArchiveCollaboratorRepository.class);
    private final ArchiveAccessGuard accessGuard = new ArchiveAccessGuard(collaboratorRepository);
    private final ArchiveRepository archiveRepository = mock(ArchiveRepository.class);
    private final ArchiveSubscriptionAuthInterceptor interceptor =
            new ArchiveSubscriptionAuthInterceptor(archiveRepository, accessGuard);

    private Archive archive() {
        User owner = User.builder().nickname("소유자").build();
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);
        Archive archive = Archive.builder().owner(owner).title("t").theme(ArchiveTheme.POLAROID).build();
        ReflectionTestUtils.setField(archive, "id", ARCHIVE_ID);
        return archive;
    }

    private Message<byte[]> subscribeMessage(String destination, Object user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (user instanceof Principal principal) {
            accessor.setUser(principal);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> connectMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private StompPrincipal principal(Long userId) {
        return new StompPrincipal(new AuthPrincipal(userId, Role.USER));
    }

    @Test
    @DisplayName("소유자면 구독을 통과시킨다")
    void 소유자는_통과() {
        given(archiveRepository.findById(ARCHIVE_ID)).willReturn(Optional.of(archive()));

        assertThatCode(() -> interceptor.preSend(
                subscribeMessage("/topic/archives/5", principal(OWNER_ID)), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("참여자가 아니면 구독을 거부한다 — 아무나 아카이브 실시간 피드를 엿볼 수 없어야 한다")
    void 참여자가_아니면_거부() {
        given(archiveRepository.findById(ARCHIVE_ID)).willReturn(Optional.of(archive()));

        assertThatThrownBy(() -> interceptor.preSend(
                subscribeMessage("/topic/archives/5", principal(STRANGER_ID)), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("존재하지 않는 아카이브면 ARCHIVE_NOT_FOUND로 거부한다")
    void 없는_아카이브면_거부() {
        given(archiveRepository.findById(ARCHIVE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preSend(
                subscribeMessage("/topic/archives/5", principal(OWNER_ID)), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARCHIVE_NOT_FOUND);
    }

    @Test
    @DisplayName("인증 안 된 구독은 거부한다")
    void 인증_안됐으면_거부() {
        assertThatThrownBy(() -> interceptor.preSend(subscribeMessage("/topic/archives/5", null), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("아카이브 토픽이 아니면 그냥 통과시킨다")
    void 아카이브_토픽이_아니면_통과() {
        assertThatCode(() -> interceptor.preSend(
                subscribeMessage("/topic/something-else", principal(STRANGER_ID)), null))
                .doesNotThrowAnyException();
        verifyNoInteractions(archiveRepository);
    }

    @Test
    @DisplayName("CONNECT 프레임은 건드리지 않는다 — 그건 StompAuthChannelInterceptor의 몫이다")
    void CONNECT는_통과() {
        assertThatCode(() -> interceptor.preSend(connectMessage(), null))
                .doesNotThrowAnyException();
        verifyNoInteractions(archiveRepository);
    }
}
