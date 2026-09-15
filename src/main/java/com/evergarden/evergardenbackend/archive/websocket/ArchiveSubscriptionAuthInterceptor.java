package com.evergarden.evergardenbackend.archive.websocket;

import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.archive.service.ArchiveAccessGuard;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.security.StompPrincipal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * 아카이브 토픽 구독을 참여자만 되게 막는다(ADR-017).
 *
 * <p>{@code SessionSubscribeEvent}로는 이걸 못 한다 — 그 이벤트는 브로커가 구독을
 * 이미 등록한 뒤에 발행돼서, 거기서 참여자가 아니란 걸 알아도 이미 늦는다
 * ({@link ArchiveEditorPresenceListener}는 그래서 editor.joined를 보낼지 판단하는
 * 용도로만 쓴다). 등록 자체를 막으려면 브로커한테 넘어가기 전인 여기, 즉
 * {@code clientInboundChannel} 인터셉터에서 SUBSCRIBE 프레임 자체를 거부해야 한다.
 *
 * <p>{@link com.evergarden.evergardenbackend.global.security.StompAuthChannelInterceptor}와
 * 굳이 합치지 않는다 — 그쪽은 "로그인했는가"(CONNECT)만 보는 범용 인증이고, 이건
 * "이 아카이브를 볼 자격이 있는가"라는 아카이브 도메인 지식이 필요한 인가라서 계층이 다르다.
 */
@Component
@RequiredArgsConstructor
public class ArchiveSubscriptionAuthInterceptor implements ChannelInterceptor {

    private static final Pattern ARCHIVE_TOPIC = Pattern.compile("^/topic/archives/(\\d+)$");

    private final ArchiveRepository archiveRepository;
    private final ArchiveAccessGuard accessGuard;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        Long archiveId = archiveIdOf(accessor.getDestination());
        if (archiveId == null) {
            return message;
        }

        Long userId = userIdOf(accessor);
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        Archive archive = archiveRepository.findById(archiveId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARCHIVE_NOT_FOUND));
        accessGuard.checkViewable(archive, userId);
        return message;
    }

    private Long archiveIdOf(String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = ARCHIVE_TOPIC.matcher(destination);
        return matcher.matches() ? Long.valueOf(matcher.group(1)) : null;
    }

    private Long userIdOf(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof StompPrincipal principal)) {
            return null;
        }
        return principal.authPrincipal().userId();
    }
}
