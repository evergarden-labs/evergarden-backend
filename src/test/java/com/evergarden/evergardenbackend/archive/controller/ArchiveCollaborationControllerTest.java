package com.evergarden.evergardenbackend.archive.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.archive.dto.ArchiveDetail;
import com.evergarden.evergardenbackend.archive.dto.CollaborationSession;
import com.evergarden.evergardenbackend.archive.dto.CollaboratorResponse;
import com.evergarden.evergardenbackend.archive.service.ArchiveCollaborationService;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.exception.GlobalExceptionHandler;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.global.security.Role;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** ARCH-10·11·13·14·18. `@Valid` 검증과 서비스 위임만 확인한다. */
class ArchiveCollaborationControllerTest {

    private final ArchiveCollaborationService collaborationService = mock(ArchiveCollaborationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ArchiveCollaborationController(collaborationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthPrincipal(1L, Role.USER), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("userId가 없으면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 초대_userId_누락() throws Exception {
        mvc.perform(post("/archives/1/collaborators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(collaborationService);
    }

    @Test
    @DisplayName("이미 초대한 사용자면 서비스 예외가 그대로 전달된다")
    void 초대_중복() throws Exception {
        given(collaborationService.invite(eq(1L), eq(1L), eq(2L)))
                .willThrow(new BusinessException(ErrorCode.ALREADY_INVITED));

        mvc.perform(post("/archives/1/collaborators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_INVITED"));
    }

    @Test
    @DisplayName("정상 초대는 서비스에 위임한다")
    void 정상_초대() throws Exception {
        given(collaborationService.invite(eq(1L), eq(1L), eq(2L)))
                .willReturn(mock(CollaboratorResponse.class));

        mvc.perform(post("/archives/1/collaborators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":2}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("수락은 서비스에 위임한다")
    void 정상_수락() throws Exception {
        given(collaborationService.accept(eq(1L), eq(1L))).willReturn(mock(CollaboratorResponse.class));

        mvc.perform(post("/archives/1/collaborators/me/accept"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("거절은 빈 성공 봉투를 돌려준다")
    void 정상_거절() throws Exception {
        mvc.perform(post("/archives/1/collaborators/me/decline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("나가기는 빈 성공 봉투를 돌려준다")
    void 정상_나가기() throws Exception {
        mvc.perform(delete("/archives/1/collaborators/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("세션 조회는 서비스가 돌려준 접속 정보를 그대로 내려준다")
    void 정상_세션조회() throws Exception {
        given(collaborationService.getSession(1L, 1L)).willReturn(
                new CollaborationSession("ws://localhost:8080/ws", "/topic/archives/1", List.of()));

        mvc.perform(get("/archives/1/collaboration/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.websocketUrl").value("ws://localhost:8080/ws"))
                .andExpect(jsonPath("$.data.topic").value("/topic/archives/1"))
                .andExpect(jsonPath("$.data.activeEditors").isArray());
    }

    @Test
    @DisplayName("공동편집이 닫혀 있으면 세션 조회도 서비스 예외를 그대로 전달한다")
    void 세션조회_닫힌_공동편집() throws Exception {
        given(collaborationService.getSession(1L, 1L))
                .willThrow(new BusinessException(ErrorCode.COLLABORATION_CLOSED));

        mvc.perform(get("/archives/1/collaboration/session"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COLLABORATION_CLOSED"));
    }

    @Test
    @DisplayName("종료는 서비스에 위임한다")
    void 정상_종료() throws Exception {
        given(collaborationService.close(eq(1L), eq(1L))).willReturn(mock(ArchiveDetail.class));

        mvc.perform(post("/archives/1/collaboration/close"))
                .andExpect(status().isOk());
    }
}
