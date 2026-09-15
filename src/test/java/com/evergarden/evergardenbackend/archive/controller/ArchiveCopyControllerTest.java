package com.evergarden.evergardenbackend.archive.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.archive.dto.ArchiveDetail;
import com.evergarden.evergardenbackend.archive.service.ArchiveCopyService;
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

/** ARCH-15·16. `@Valid` 검증과 서비스 위임만 확인한다. */
class ArchiveCopyControllerTest {

    private final ArchiveCopyService archiveCopyService = mock(ArchiveCopyService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ArchiveCopyController(archiveCopyService))
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

    // ── duplicate ────────────────────────────────────────────────

    @Test
    @DisplayName("본문을 아예 안 보내도 정상 처리된다 — title은 null로 위임")
    void 본문_없이_복제() throws Exception {
        given(archiveCopyService.duplicate(eq(1L), eq(5L), isNull())).willReturn(mock(ArchiveDetail.class));

        mvc.perform(post("/archives/5/duplicate"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("title을 보내면 그대로 서비스에 위임한다")
    void 제목_지정_복제() throws Exception {
        given(archiveCopyService.duplicate(eq(1L), eq(5L), eq("내 복사본")))
                .willReturn(mock(ArchiveDetail.class));

        mvc.perform(post("/archives/5/duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"내 복사본"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("title이 60자를 넘으면 INVALID_REQUEST")
    void 제목_너무_김() throws Exception {
        String longTitle = "가".repeat(61);

        mvc.perform(post("/archives/5/duplicate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + longTitle + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── importShared ─────────────────────────────────────────────

    @Test
    @DisplayName("title이 없으면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 가져오기_제목_누락() throws Exception {
        mvc.perform(post("/posts/1/archive/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("정상 요청은 서비스에 위임한다")
    void 가져오기_정상() throws Exception {
        given(archiveCopyService.importShared(eq(1L), eq(1L), eq("가져온 아카이브")))
                .willReturn(mock(ArchiveDetail.class));

        mvc.perform(post("/posts/1/archive/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"가져온 아카이브"}
                                """))
                .andExpect(status().isOk());
    }
}
