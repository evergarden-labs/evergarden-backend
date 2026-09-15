package com.evergarden.evergardenbackend.archive.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.archive.dto.ArchiveDetail;
import com.evergarden.evergardenbackend.archive.dto.ArchiveItemResponse;
import com.evergarden.evergardenbackend.archive.service.ArchiveItemService;
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

/** `@Valid` 검증과 서비스 위임만 확인한다. 권한 자체는 {@code SecurityConfigTest}가 검증한다. */
class ArchiveItemControllerTest {

    private final ArchiveItemService archiveItemService = mock(ArchiveItemService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ArchiveItemController(archiveItemService))
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
    @DisplayName("items가 비어 있으면 INVALID_REQUEST — 서비스를 부르지 않는다")
    void 빈_items() throws Exception {
        mvc.perform(post("/archives/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(archiveItemService);
    }

    @Test
    @DisplayName("mediaId가 없으면 INVALID_REQUEST")
    void mediaId_누락() throws Exception {
        mvc.perform(post("/archives/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"caption":"캡션만 있음"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("정상 요청은 서비스에 위임하고 결과를 그대로 돌려준다")
    void 정상_추가() throws Exception {
        given(archiveItemService.addItems(eq(1L), eq(1L), any()))
                .willReturn(List.of(mock(ArchiveItemResponse.class)));

        mvc.perform(post("/archives/1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"mediaId":10}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("보내지 않은 필드가 없다는 검증은 서비스가 하므로, 서비스가 던진 오류가 그대로 전달된다")
    void 빈_항목_수정은_서비스_예외가_전달된다() throws Exception {
        given(archiveItemService.updateItem(eq(1L), eq(1L), eq(2L), any()))
                .willThrow(new BusinessException(ErrorCode.INVALID_REQUEST));

        mvc.perform(patch("/archives/1/items/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("삭제는 빈 성공 봉투를 돌려준다")
    void 삭제_성공() throws Exception {
        mvc.perform(delete("/archives/1/items/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("레이아웃 일괄 변경은 items가 비면 INVALID_REQUEST")
    void 빈_레이아웃_요청() throws Exception {
        mvc.perform(put("/archives/1/items/layout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("대표 사진 지정은 itemId가 없으면 INVALID_REQUEST")
    void 대표사진_itemId_누락() throws Exception {
        mvc.perform(put("/archives/1/cover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("정상 대표 사진 지정은 서비스에 위임한다")
    void 정상_대표사진_지정() throws Exception {
        given(archiveItemService.setCover(eq(1L), eq(1L), any())).willReturn(mock(ArchiveDetail.class));

        mvc.perform(put("/archives/1/cover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":2}"))
                .andExpect(status().isOk());
    }
}
