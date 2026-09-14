package com.evergarden.evergardenbackend.media.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.global.exception.GlobalExceptionHandler;
import com.evergarden.evergardenbackend.global.security.AuthPrincipal;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.media.dto.MediaUploadTicket;
import com.evergarden.evergardenbackend.media.service.MediaService;
import java.time.Instant;
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

/**
 * 요청 검증({@code @Valid})과 서비스 위임만 확인한다. 인증·인가 자체는
 * {@code SecurityConfigTest}가 필터 체인 전체로 이미 검증한다 — 여기서는 필터를
 * 거치지 않고 {@link AuthPrincipal}을 직접 세팅한다.
 */
class MediaControllerTest {

    private final MediaService mediaService = mock(MediaService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MediaController(mediaService))
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
    @DisplayName("files가 비어 있으면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 업로드_URL_발급_files_비어있음() throws Exception {
        mvc.perform(post("/media/upload-urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(mediaService);
    }

    @Test
    @DisplayName("fileName이 빈 문자열이면 INVALID_REQUEST")
    void 업로드_URL_발급_fileName_누락() throws Exception {
        mvc.perform(post("/media/upload-urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"files":[{"fileName":"","contentType":"image/jpeg","sizeBytes":1000}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("정상 요청은 로그인한 사용자 ID로 서비스에 위임하고 결과를 그대로 돌려준다")
    void 업로드_URL_발급_정상_요청() throws Exception {
        given(mediaService.issueUploadUrls(eq(1L), anyList())).willReturn(List.of(
                new MediaUploadTicket(10L, "https://upload.example.com/x", Instant.parse("2026-09-14T00:00:00Z"))));

        mvc.perform(post("/media/upload-urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"files":[{"fileName":"a.jpg","contentType":"image/jpeg","sizeBytes":1000}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].mediaId").value(10))
                .andExpect(jsonPath("$.data[0].uploadUrl").value("https://upload.example.com/x"));
    }

    @Test
    @DisplayName("mediaIds가 비어 있으면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 완료_통보_mediaIds_비어있음() throws Exception {
        mvc.perform(post("/media/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(mediaService);
    }
}
