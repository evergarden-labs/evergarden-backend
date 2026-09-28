package com.evergarden.evergardenbackend.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.notification.entity.NotificationTargetType;
import com.evergarden.evergardenbackend.notification.entity.NotificationType;
import com.evergarden.evergardenbackend.notification.service.NotificationService;
import com.evergarden.evergardenbackend.support.IntegrationTest;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 PostgreSQL로 발송({@link NotificationService#notify}, 이미 있는 발송 로직)부터
 * 목록·배지·읽음 처리·설정까지 전체 흐름을 확인한다. 발송 쪽과의 연결고리
 * (설정을 꺼두면 발송 자체가 안 쌓이는지)도 여기서만 확인할 수 있다.
 */
@AutoConfigureMockMvc
@Transactional
class NotificationIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired NotificationService notificationService;

    @Test
    @DisplayName("발송 → 목록 조회 → 읽음 처리 → 배지 감소까지 전체 흐름")
    void 발송_목록_읽음_배지_흐름() throws Exception {
        User user = userRepository.save(User.builder().nickname("여행자1001").build());
        String accessToken = tokenProvider.issueAccessToken(user.getId(), Role.USER);

        notificationService.notify(user, NotificationType.COLLAB_INVITE, "공동 편집 초대",
                "아카이브에 초대됐어요", NotificationTargetType.ARCHIVE, 1L);
        notificationService.notify(user, NotificationType.WARNING, "경고 안내", "정책 위반", null, null);

        mvc.perform(get("/notifications/unread-count").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2));

        MvcResult listResult = mvc.perform(get("/notifications").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();
        JsonNode data = jsonMapper.readTree(listResult.getResponse().getContentAsString()).path("data");
        long firstNotificationId = data.get(0).path("notificationId").asLong();

        mvc.perform(patch("/notifications/" + firstNotificationId + "/read")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRead").value(true));

        mvc.perform(get("/notifications/unread-count").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        mvc.perform(post("/notifications/read-all").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mvc.perform(get("/notifications/unread-count").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    @DisplayName("끌 수 있는 알림을 꺼두면 그 종류는 발송해도 안 쌓인다 — 발송 쪽 기본값 로직과의 연결고리")
    void 설정_꺼두면_발송_안쌓임() throws Exception {
        User user = userRepository.save(User.builder().nickname("여행자1002").build());
        String accessToken = tokenProvider.issueAccessToken(user.getId(), Role.USER);

        mvc.perform(patch("/notifications/settings")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"settings":[{"type":"COLLAB_INVITE","enabled":false}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.type=='COLLAB_INVITE')].enabled").value(false));

        notificationService.notify(user, NotificationType.COLLAB_INVITE, "공동 편집 초대",
                "아카이브에 초대됐어요", NotificationTargetType.ARCHIVE, 1L);
        notificationService.notify(user, NotificationType.WARNING, "경고 안내", "정책 위반", null, null);

        mvc.perform(get("/notifications/unread-count").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    @DisplayName("경고 알림은 설정에서 끌 수 없다 — 시도하면 400, 실제로 안 꺼진다")
    void 설정_경고끄기_거절() throws Exception {
        User user = userRepository.save(User.builder().nickname("여행자1003").build());
        String accessToken = tokenProvider.issueAccessToken(user.getId(), Role.USER);

        mvc.perform(patch("/notifications/settings")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"settings":[{"type":"WARNING","enabled":false}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        mvc.perform(get("/notifications/settings").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.type=='WARNING')].enabled").value(true))
                .andExpect(jsonPath("$.data[?(@.type=='WARNING')].editable").value(false));
    }
}
