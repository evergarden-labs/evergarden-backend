package com.evergarden.evergardenbackend.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.global.config.SecurityConfig;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.security.DevUserProvider;
import com.evergarden.evergardenbackend.global.security.JwtAccessDeniedHandler;
import com.evergarden.evergardenbackend.global.security.JwtAuthenticationEntryPoint;
import com.evergarden.evergardenbackend.global.security.JwtAuthenticationFilter;
import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.global.security.SecurityErrorResponder;
import com.evergarden.evergardenbackend.notification.dto.NotificationResponse;
import com.evergarden.evergardenbackend.notification.dto.NotificationSettingResponse;
import com.evergarden.evergardenbackend.notification.dto.UnreadCount;
import com.evergarden.evergardenbackend.notification.entity.NotificationType;
import com.evergarden.evergardenbackend.notification.service.NotificationQueryService;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 인증 필요·페이지네이션·검증·서비스 위임을 확인한다. */
@ActiveProfiles("test")
@WebMvcTest(controllers = NotificationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class NotificationControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean NotificationQueryService notificationQueryService;

    String accessToken;

    @BeforeEach
    void setUp() {
        User activeUser = User.builder().nickname("여행자").build();
        given(userRepository.findById(any())).willReturn(Optional.of(activeUser));
        accessToken = tokenProvider.issueAccessToken(1L, Role.USER);
    }

    private NotificationResponse sampleNotification(boolean read) {
        return new NotificationResponse(1L, NotificationType.WARNING, "경고", "사유", null, null, read,
                LocalDateTime.now());
    }

    // ── 목록·배지 ────────────────────────────────────────────

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 목록_비로그인() throws Exception {
        mvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("번호 페이지네이션으로 서비스에 위임한다")
    void 목록_정상() throws Exception {
        given(notificationQueryService.listNotifications(eq(1L), any()))
                .willReturn(new PageImpl<>(List.of(sampleNotification(false))));

        mvc.perform(get("/notifications?page=1&size=20").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].isRead").value(false))
                .andExpect(jsonPath("$.meta.page").value(1));
    }

    @Test
    @DisplayName("배지 개수를 조회한다")
    void 안읽은개수_정상() throws Exception {
        given(notificationQueryService.getUnreadCount(1L)).willReturn(new UnreadCount(5));

        mvc.perform(get("/notifications/unread-count").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(5));
    }

    // ── 읽음 처리 ────────────────────────────────────────────

    @Test
    @DisplayName("모두 읽음 처리 후 unreadCount 0을 받는다")
    void 모두읽음_정상() throws Exception {
        given(notificationQueryService.markAllNotificationsRead(1L)).willReturn(new UnreadCount(0));

        mvc.perform(post("/notifications/read-all").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    @DisplayName("하나 읽음 처리는 로그인한 사용자 ID·경로변수로 위임한다")
    void 읽음처리_정상() throws Exception {
        given(notificationQueryService.markNotificationRead(1L, 10L)).willReturn(sampleNotification(true));

        mvc.perform(patch("/notifications/10/read").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRead").value(true));
    }

    @Test
    @DisplayName("서비스가 던진 NOT_RESOURCE_OWNER가 그대로 전달된다")
    void 읽음처리_소유자아님_전달() throws Exception {
        given(notificationQueryService.markNotificationRead(1L, 10L))
                .willThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER));

        mvc.perform(patch("/notifications/10/read").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_RESOURCE_OWNER"));
    }

    // ── 설정 ────────────────────────────────────────────────

    @Test
    @DisplayName("설정 목록을 조회한다")
    void 설정_조회_정상() throws Exception {
        given(notificationQueryService.getNotificationSettings(1L))
                .willReturn(List.of(new NotificationSettingResponse(NotificationType.WARNING, true, false)));

        mvc.perform(get("/notifications/settings").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].editable").value(false));
    }

    @Test
    @DisplayName("설정 변경 요청이 비어 있으면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 설정_변경_빈배열() throws Exception {
        mvc.perform(patch("/notifications/settings")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"settings":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("서비스가 던진 INVALID_REQUEST(WARNING 끄기 시도)가 그대로 전달된다")
    void 설정_변경_경고끄기_전달() throws Exception {
        given(notificationQueryService.updateNotificationSettings(eq(1L), any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_REQUEST));

        mvc.perform(patch("/notifications/settings")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"settings":[{"type":"WARNING","enabled":false}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }
}
