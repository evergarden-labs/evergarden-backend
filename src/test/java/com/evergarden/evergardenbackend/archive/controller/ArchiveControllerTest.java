package com.evergarden.evergardenbackend.archive.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.archive.dto.ArchiveDetail;
import com.evergarden.evergardenbackend.archive.service.ArchiveService;
import com.evergarden.evergardenbackend.global.config.SecurityConfig;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.exception.GlobalExceptionHandler;
import com.evergarden.evergardenbackend.global.response.CursorMeta;
import com.evergarden.evergardenbackend.global.response.CursorPage;
import com.evergarden.evergardenbackend.global.security.DevUserProvider;
import com.evergarden.evergardenbackend.global.security.JwtAccessDeniedHandler;
import com.evergarden.evergardenbackend.global.security.JwtAuthenticationEntryPoint;
import com.evergarden.evergardenbackend.global.security.JwtAuthenticationFilter;
import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.global.security.SecurityErrorResponder;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * `@Valid`·`@Min`/`@Max` 검증이 실제로 걸리는지 확인한다. 이건 서비스 단위 테스트로는
 * 확인할 수 없다 — HTTP 요청이 DTO로 바뀌는 과정 자체를 거치지 않기 때문이다.
 *
 * <p>{@code standaloneSetup}이 아니라 {@code @WebMvcTest}를 쓴다 — {@code size} 파라미터의
 * {@code @Min}/{@code @Max}는 {@code @Validated} AOP 프록시로 동작하는데, 이건 실제
 * 스프링 컨텍스트가 있어야 만들어진다({@code SecurityConfigTest}와 같은 이유).
 */
@ActiveProfiles("test")
@WebMvcTest(controllers = ArchiveController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class ArchiveControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean ArchiveService archiveService;

    String accessToken;

    @BeforeEach
    void setUp() {
        User activeUser = User.builder().nickname("여행자").build();
        given(userRepository.findById(any())).willReturn(Optional.of(activeUser));
        accessToken = tokenProvider.issueAccessToken(1L, Role.USER);
    }

    // ── 목록: @Min/@Max ──────────────────────────────────────────

    @Test
    @DisplayName("size가 0이면 INVALID_REQUEST")
    void size_0이면_거절() throws Exception {
        mvc.perform(get("/archives?size=0").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("size가 50을 넘으면 INVALID_REQUEST")
    void size_초과면_거절() throws Exception {
        mvc.perform(get("/archives?size=51").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("기본 size(20)로 정상 조회된다")
    void 기본_size로_조회() throws Exception {
        given(archiveService.list(eq(1L), isNull(), eq(20)))
                .willReturn(new CursorPage<>(List.of(), CursorMeta.last()));

        mvc.perform(get("/archives").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ── 생성: @Valid ─────────────────────────────────────────────

    @Test
    @DisplayName("title이 비어 있으면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 빈_제목() throws Exception {
        mvc.perform(post("/archives")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","theme":"POLAROID"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("primaryColor 형식이 틀리면 INVALID_REQUEST")
    void 잘못된_색상_형식() throws Exception {
        mvc.perform(post("/archives")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제주","theme":"POLAROID","primaryColor":"FF5733"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("theme이 없으면 INVALID_REQUEST")
    void 테마_누락() throws Exception {
        mvc.perform(post("/archives")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제주"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("정상 요청은 로그인한 사용자 ID로 서비스에 위임한다")
    void 정상_생성() throws Exception {
        ArchiveDetail detail = mock(ArchiveDetail.class);
        given(archiveService.create(eq(1L), any())).willReturn(detail);

        mvc.perform(post("/archives")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제주도 여행","theme":"POLAROID","primaryColor":"#FF5733"}
                                """))
                .andExpect(status().isOk());
    }

    // ── 조회·삭제: 서비스 예외가 그대로 전달되는지 ──────────────────

    @Test
    @DisplayName("없는 아카이브 조회는 404 ARCHIVE_NOT_FOUND")
    void 없는_아카이브() throws Exception {
        given(archiveService.get(eq(1L), eq(999L)))
                .willThrow(new BusinessException(ErrorCode.ARCHIVE_NOT_FOUND));

        mvc.perform(get("/archives/999").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ARCHIVE_NOT_FOUND"));
    }

    @Test
    @DisplayName("소유자가 아니면 수정 시 403 NOT_RESOURCE_OWNER")
    void 소유자가_아닌_수정() throws Exception {
        given(archiveService.update(eq(1L), eq(5L), any()))
                .willThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER));

        mvc.perform(patch("/archives/5")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"새 이름"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_RESOURCE_OWNER"));
    }

    @Test
    @DisplayName("삭제는 빈 성공 봉투를 돌려준다")
    void 삭제_성공() throws Exception {
        mvc.perform(delete("/archives/5").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
