package com.evergarden.evergardenbackend.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 오류 응답이 명세의 봉투 모양대로 나가는지 확인한다(ADR-008). */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("BusinessException은 코드의 상태와 메시지로 나간다")
    void 비즈니스_예외() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("여행 일정을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.error.details").doesNotExist())
                // 성공 봉투 필드가 섞이지 않는다
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("details를 넘기면 그대로 담겨 나간다")
    void 부가정보가_담긴다() throws Exception {
        mockMvc.perform(get("/test/blocked"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("USER_BLOCKED"))
                .andExpect(jsonPath("$.error.details.reason").value("반복적인 부적절한 게시물"));
    }

    @Test
    @DisplayName("검증 실패는 어느 필드가 왜 틀렸는지 알려준다")
    void 검증_실패() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.details.fields[0].field").value("title"))
                .andExpect(jsonPath("$.error.details.fields[0].reason").isNotEmpty());
    }

    @Test
    @DisplayName("본문이 JSON이 아니면 INVALID_REQUEST다")
    void 잘못된_본문() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ 이건 JSON이 아님"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("예상 못 한 예외는 내부 사정을 감춘다")
    void 예상하지_못한_예외() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                // 예외 메시지가 그대로 새어 나가면 안 된다
                .andExpect(jsonPath("$.error.message")
                        .value("일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        record Request(@NotBlank String title) {
        }

        @org.springframework.web.bind.annotation.GetMapping("/not-found")
        void notFound() {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }

        @org.springframework.web.bind.annotation.GetMapping("/blocked")
        void blocked() {
            throw new BusinessException(ErrorCode.USER_BLOCKED,
                    Map.of("reason", "반복적인 부적절한 게시물"));
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody Request request) {
        }

        @org.springframework.web.bind.annotation.GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("DB 커넥션 풀 고갈: evergarden.users 테이블");
        }
    }
}
