package com.evergarden.evergardenbackend.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.media.entity.Media;
import com.evergarden.evergardenbackend.media.entity.MediaType;
import com.evergarden.evergardenbackend.media.repository.MediaRepository;
import com.evergarden.evergardenbackend.media.service.MediaStorageService;
import com.evergarden.evergardenbackend.support.IntegrationTest;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 PostgreSQL·스프링 컨텍스트로 생성→아이템 추가→기간 계산→복제 흐름 전체를 확인한다.
 *
 * <p>{@link MediaStorageService}(S3)만 목으로 두고 나머지는 전부 진짜 빈이다 — 시큐리티 필터,
 * 검증, 서비스, 리포지토리까지 실제로 엮여 도는지가 목적이다. 미디어는 업로드 흐름을 다시
 * 타지 않고 {@link MediaRepository}로 바로 READY 상태를 만든다 — 그건 {@code MediaIntegrationTest}가
 * 이미 확인했다.
 */
@AutoConfigureMockMvc
@Transactional
class ArchiveIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired MediaRepository mediaRepository;

    @MockitoBean MediaStorageService storageService;

    User owner;
    String accessToken;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder().nickname("테스터").build());
        accessToken = tokenProvider.issueAccessToken(owner.getId(), Role.USER);

        given(storageService.presignDownload(anyString()))
                .willAnswer(invocation -> "https://cdn.example.com/" + invocation.getArgument(0));
    }

    @Test
    @DisplayName("생성 → 아이템 추가 → 기간 계산 → 복제까지 전체 흐름이 돈다")
    void 생성부터_복제까지() throws Exception {
        MvcResult createResult = mvc.perform(post("/archives")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제주 여행","theme":"POLAROID"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").doesNotExist())
                .andExpect(jsonPath("$.data.itemCount").value(0))
                .andReturn();
        Long archiveId = at(createResult, "/data/archiveId");

        Media early = readyMedia(LocalDateTime.of(2026, 3, 1, 9, 0));
        Media late = readyMedia(LocalDateTime.of(2026, 3, 5, 18, 0));

        MvcResult addResult = mvc.perform(post("/archives/" + archiveId + "/items")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"mediaId":%d},{"mediaId":%d}]}
                                """.formatted(early.getId(), late.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();
        Long firstItemId = at(addResult, "/data/0/itemId");

        mvc.perform(get("/archives/" + archiveId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value("2026-03-01"))
                .andExpect(jsonPath("$.data.endDate").value("2026-03-05"))
                .andExpect(jsonPath("$.data.itemCount").value(2));

        mvc.perform(put("/archives/" + archiveId + "/cover")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":" + firstItemId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverImageUrl").exists());

        MvcResult duplicateResult = mvc.perform(post("/archives/" + archiveId + "/duplicate")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("제주 여행"))
                .andExpect(jsonPath("$.data.originArchiveId").value(archiveId))
                .andExpect(jsonPath("$.data.itemCount").value(2))
                .andExpect(jsonPath("$.data.startDate").value("2026-03-01"))
                .andExpect(jsonPath("$.data.endDate").value("2026-03-05"))
                .andReturn();

        JsonNode copy = jsonMapper.readTree(duplicateResult.getResponse().getContentAsString()).at("/data");
        assertThat(copy.at("/archiveId").asLong()).isNotEqualTo(archiveId);
        assertThat(copy.at("/items").size()).isEqualTo(2);
        assertThat(copy.at("/items/0/media/mediaId").asLong()).isEqualTo(early.getId());
        assertThat(copy.at("/items/1/media/mediaId").asLong()).isEqualTo(late.getId());
        assertThat(copy.at("/items/0/isCover").asBoolean())
                .as("원본의 대표 사진 지정도 그대로 복사돼야 한다")
                .isTrue();
    }

    private Media readyMedia(LocalDateTime takenAt) {
        Media media = Media.builder()
                .uploader(owner)
                .type(MediaType.IMAGE)
                .storageKey("archives/" + owner.getId() + "/original.jpg")
                .sizeBytes(1000L)
                .width(800)
                .height(600)
                .build();
        media.complete(null, 800, 600, takenAt, null, null);
        return mediaRepository.save(media);
    }

    private Long at(MvcResult result, String path) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString()).at(path).asLong();
    }
}
