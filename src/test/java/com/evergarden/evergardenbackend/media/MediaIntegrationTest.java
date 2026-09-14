package com.evergarden.evergardenbackend.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.media.entity.Media;
import com.evergarden.evergardenbackend.media.entity.MediaStatus;
import com.evergarden.evergardenbackend.media.repository.MediaRepository;
import com.evergarden.evergardenbackend.media.service.MediaStorageService;
import com.evergarden.evergardenbackend.support.IntegrationTest;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 PostgreSQL·스프링 컨텍스트로 URL 발급 → 완료 통보 전체 흐름을 확인한다(MEDIA-01).
 *
 * <p>{@link MediaStorageService}(S3)만 목으로 두고 나머지는 전부 진짜다 — 시큐리티 필터,
 * 검증, 서비스, 리포지토리, EXIF·썸네일 로직까지 실제 빈으로 엮여 도는지가 이 테스트의 목적이다.
 * S3까지 진짜로 때리면 CI에 자격증명이 있어야 하고 매번 비용·지연이 생겨 여기서는 막는다 —
 * 진짜 S3 연동은 수동으로 이미 확인했다.
 *
 * <p>{@code @Transactional}로 각 테스트를 롤백한다. 닉네임이 유니크 제약이라
 * 테스트마다 유저를 새로 만들면 롤백 없이는 두 번째 테스트부터 충돌한다.
 */
@AutoConfigureMockMvc
@Transactional
class MediaIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired MediaRepository mediaRepository;

    @MockitoBean MediaStorageService storageService;

    String accessToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder().nickname("테스터").build());
        accessToken = tokenProvider.issueAccessToken(user.getId(), Role.USER);

        given(storageService.presignUpload(anyString(), anyString())).willReturn(
                new MediaStorageService.UploadTicket("https://upload.example.com/x", Instant.now().plusSeconds(600)));
        given(storageService.presignDownload(anyString()))
                .willAnswer(invocation -> "https://cdn.example.com/" + invocation.getArgument(0));
    }

    @Test
    @DisplayName("URL 발급 → 업로드 확인 → 완료까지 전체 흐름이 돈다")
    void 업로드_URL_발급부터_완료까지() throws Exception {
        MvcResult issueResult = mvc.perform(post("/media/upload-urls")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"files":[{"fileName":"a.jpg","contentType":"image/jpeg","sizeBytes":1000}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].mediaId").exists())
                .andReturn();

        Long mediaId = mediaId(issueResult);
        Media pending = mediaRepository.findById(mediaId).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(MediaStatus.PENDING);

        given(storageService.exists(pending.getStorageKey())).willReturn(true);
        given(storageService.download(pending.getStorageKey())).willReturn(plainJpeg());

        mvc.perform(post("/media/complete")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaIds\":[" + mediaId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("READY"))
                .andExpect(jsonPath("$.data[0].url").value("https://cdn.example.com/" + pending.getStorageKey()))
                .andExpect(jsonPath("$.data[0].thumbnailUrl").exists());

        Media ready = mediaRepository.findById(mediaId).orElseThrow();
        assertThat(ready.getStatus()).isEqualTo(MediaStatus.READY);
        assertThat(ready.getThumbnailKey()).isNotNull();
    }

    @Test
    @DisplayName("스토리지에 실제로 올라오지 않았으면 완료 통보가 MEDIA_UPLOAD_INCOMPLETE로 거절된다")
    void 업로드가_끝나지_않으면_거절된다() throws Exception {
        MvcResult issueResult = mvc.perform(post("/media/upload-urls")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"files":[{"fileName":"a.jpg","contentType":"image/jpeg","sizeBytes":1000}]}
                                """))
                .andReturn();
        Long mediaId = mediaId(issueResult);

        given(storageService.exists(anyString())).willReturn(false);

        mvc.perform(post("/media/complete")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaIds\":[" + mediaId + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MEDIA_UPLOAD_INCOMPLETE"))
                .andExpect(jsonPath("$.error.details.mediaIds[0]").value(mediaId));

        assertThat(mediaRepository.findById(mediaId).orElseThrow().getStatus()).isEqualTo(MediaStatus.PENDING);
    }

    @Test
    @DisplayName("남이 발급받은 mediaId로 완료 통보하면 NOT_RESOURCE_OWNER")
    void 남의_미디어면_거절된다() throws Exception {
        MvcResult issueResult = mvc.perform(post("/media/upload-urls")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"files":[{"fileName":"a.jpg","contentType":"image/jpeg","sizeBytes":1000}]}
                                """))
                .andReturn();
        Long mediaId = mediaId(issueResult);

        User other = userRepository.save(User.builder().nickname("다른사람").build());
        String otherToken = tokenProvider.issueAccessToken(other.getId(), Role.USER);

        mvc.perform(post("/media/complete")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaIds\":[" + mediaId + "]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_RESOURCE_OWNER"));
    }

    private Long mediaId(MvcResult result) throws Exception {
        JsonNode root = jsonMapper.readTree(result.getResponse().getContentAsString());
        return root.at("/data/0/mediaId").asLong();
    }

    private byte[] plainJpeg() throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
