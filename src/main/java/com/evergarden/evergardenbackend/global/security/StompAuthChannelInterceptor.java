package com.evergarden.evergardenbackend.global.security;

import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.entity.UserStatus;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP {@code CONNECT} 프레임의 {@code Authorization} 헤더를 검증한다(ADR-051).
 *
 * <p>{@code GET .../collaboration/session}을 호출한 뒤 연결하기까지 사이에 권한이
 * 사라졌을 수 있어(공동 편집 종료, 나가기, 차단) 여기서 한 번 더 확인한다
 * ({@code docs/realtime-editing.md} 2.2절). 검증에 실패하면 예외를 던져 연결 자체를 끊는다 —
 * HTTP처럼 오류 봉투를 돌려줄 수 없어 REST 필터({@link JwtAuthenticationFilter})만큼
 * 사유를 세분화하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            AuthPrincipal principal = authenticate(accessor);
            accessor.setUser(new StompPrincipal(principal));
        }
        return message;
    }

    private AuthPrincipal authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new IllegalArgumentException("Authorization 헤더가 없습니다");
        }
        AuthPrincipal principal = tokenProvider.parseAccessToken(header.substring(BEARER_PREFIX.length()));

        if (principal.role() == Role.USER) {
            User user = userRepository.findById(principal.userId())
                    .orElseThrow(() -> new IllegalStateException("존재하지 않는 사용자입니다"));
            if (user.getStatus() == UserStatus.BLOCKED || user.getStatus() == UserStatus.WITHDRAWN) {
                throw new IllegalStateException("이용할 수 없는 계정입니다");
            }
        }
        return principal;
    }
}
