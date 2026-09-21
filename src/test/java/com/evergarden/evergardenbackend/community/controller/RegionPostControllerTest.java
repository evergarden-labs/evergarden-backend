package com.evergarden.evergardenbackend.community.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.community.dto.PostSummary;
import com.evergarden.evergardenbackend.community.service.PostService;
import com.evergarden.evergardenbackend.global.config.SecurityConfig;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** `@Min`/`@Max` 검증과 서비스 예외 전달을 확인한다 — 다른 컨트롤러 테스트와 같은 이유로 {@code @WebMvcTest}를 쓴다. */
@ActiveProfiles("test")
@WebMvcTest(controllers = RegionPostController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class, SecurityErrorResponder.class,
        DevUserProvider.class, JacksonAutoConfiguration.class})
class RegionPostControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @MockitoBean UserRepository userRepository;
    @MockitoBean PostService postService;

    String accessToken;

    @BeforeEach
    void setUp() {
        User activeUser = User.builder().nickname("여행자").build();
        given(userRepository.findById(any())).willReturn(Optional.of(activeUser));
        accessToken = tokenProvider.issueAccessToken(1L, Role.USER);
    }

    @Test
    @DisplayName("존재하지 않는 지역이면 서비스의 404가 그대로 전달된다")
    void 없는_지역() throws Exception {
        given(postService.listRegionPosts(eq(1L), eq("999"), any(), any(), anyInt()))
                .willThrow(new BusinessException(ErrorCode.REGION_NOT_FOUND));

        mvc.perform(get("/regions/999/posts").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REGION_NOT_FOUND"));
    }

    @Test
    @DisplayName("정상 조회는 목록과 커서 메타를 그대로 돌려준다")
    void 정상_조회() throws Exception {
        given(postService.listRegionPosts(eq(1L), eq("11"), any(), any(), anyInt()))
                .willReturn(new CursorPage<>(List.of(mock(PostSummary.class)), CursorMeta.last()));

        mvc.perform(get("/regions/11/posts").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.hasNext").value(false));
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void 비로그인() throws Exception {
        mvc.perform(get("/regions/11/posts")).andExpect(status().isUnauthorized());
    }
}
