package com.evergarden.evergardenbackend.archive.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveCollaborator;
import com.evergarden.evergardenbackend.archive.entity.ArchiveTheme;
import com.evergarden.evergardenbackend.archive.repository.ArchiveCollaboratorRepository;
import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.support.IntegrationTest;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * 60초 타임아웃으로 쫓겨난 뒤의 늦은 ping이 실제로 세션을 복구시키고
 * {@code editor.joined}를 다시 쏘는지, 진짜 WebSocket 연결로 끝까지 확인한다.
 *
 * <p>유닛 테스트(ArchivePingControllerTest)는 {@code presenceListener.userIdOf()}를
 * 목으로 고정해서 "쫓겨난 뒤 subscribe()가 true면 재발행한다"는 흐름만 본다.
 * 여기서는 그 전 단계 — CONNECT 프레임에서 세팅된 {@code StompPrincipal}이
 * 진짜 ping(SEND) 프레임까지 그대로 이어지는지(핸드셰이크 시점 principal이 잘못
 * 쓰였던 전례가 있는 지점) — 를 실제 STOMP 클라이언트로 검증한다.
 *
 * <p>{@link ArchiveEditorRegistry}를 스파이로 감싸, 서버가 실제로 쓴 세션 ID를
 * 그대로 캡처해서 {@code disconnect()}를 호출한다 — 클라이언트 쪽
 * {@code StompSession.getSessionId()}는 서버의 WebSocket 세션 ID와 같다는
 * 보장이 없어서 믿지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArchivePingRecoveryIntegrationTest extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired JwtTokenProvider tokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired ArchiveRepository archiveRepository;
    @Autowired ArchiveCollaboratorRepository collaboratorRepository;
    @MockitoSpyBean ArchiveEditorRegistry registry;

    private StompSession session;

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    @Test
    @DisplayName("쫓겨난 뒤 늦은 ping을 보내면 복구되고 editor.joined가 다시 온다")
    void 쫓겨난_뒤_ping으로_복구() throws Exception {
        User user = userRepository.save(User.builder().nickname("실시간테스터").build());
        Archive archive = archiveRepository.save(
                Archive.builder().owner(user).title("실시간 테스트").theme(ArchiveTheme.POLAROID).build());
        collaboratorRepository.save(ArchiveCollaborator.owner(archive, user, LocalDateTime.now()));
        String accessToken = tokenProvider.issueAccessToken(user.getId(), Role.USER);

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

        // /ws 핸드셰이크(HTTP 업그레이드) 자체도 SecurityConfig의 anyRequest().authenticated()를
        // 거치므로, STOMP CONNECT 프레임뿐 아니라 HTTP 핸드셰이크 헤더에도 넣어야 한다.
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add("Authorization", "Bearer " + accessToken);

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + accessToken);

        session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", handshakeHeaders, connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        // 서버가 JSON(application/json)으로 보내서, 변환기 설정에 따라 메시지가 조용히 버려질
        // 수 있다 — byte[]로 그대로 받아서 직접 문자열로 바꾼다(변환 협상 자체를 피함).
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        session.subscribe("/topic/archives/" + archive.getId(), new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.offer(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });

        // 첫 구독으로 editor.joined가 한 번 온다 — 이때 서버가 실제로 쓴 세션 ID를 캡처
        String firstJoined = received.poll(5, TimeUnit.SECONDS);
        assertThat(firstJoined).contains("\"editor.joined\"");

        ArgumentCaptor<String> sessionIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(registry, timeout(5000)).subscribe(sessionIdCaptor.capture(), eq(archive.getId()), eq(user.getId()));
        String realSessionId = sessionIdCaptor.getValue();

        // EditorPresenceScheduler가 60초 타임아웃으로 쫓아낸 상황을 흉내 — 실제로 기다리지 않는다.
        // WebSocket 연결 자체는 그대로 살아있다 (session.isConnected()는 여전히 true).
        registry.disconnect(realSessionId);

        // 같은 연결로 늦게 도착한 ping — 재연결(CONNECT+SUBSCRIBE)이 아니다
        session.send("/app/archives/" + archive.getId() + "/ping", "");

        String secondJoined = received.poll(5, TimeUnit.SECONDS);
        assertThat(secondJoined).contains("\"editor.joined\"");
    }
}
