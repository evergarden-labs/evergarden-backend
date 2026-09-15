package com.evergarden.evergardenbackend.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.media.config.StorageProperties;
import com.evergarden.evergardenbackend.media.dto.MediaResponse;
import com.evergarden.evergardenbackend.media.dto.MediaUploadRequest;
import com.evergarden.evergardenbackend.media.dto.MediaUploadTicket;
import com.evergarden.evergardenbackend.media.entity.Media;
import com.evergarden.evergardenbackend.media.entity.MediaStatus;
import com.evergarden.evergardenbackend.media.entity.MediaType;
import com.evergarden.evergardenbackend.media.repository.MediaRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * URL 발급·완료 처리의 검증 순서와 오류 코드를 확인한다(MEDIA-01).
 *
 * <p>{@link MediaStorageService}(S3)만 목으로 두고 나머지({@link ContentTypePolicy}·
 * {@link MediaKeyGenerator}·{@link ExifReader}·{@link ThumbnailGenerator})는 실제 객체를 쓴다 —
 * 순수 로직이라 목으로 바꿔도 얻을 게 없고, 오히려 실제 동작(EXIF 없음·썸네일 실패 등)을
 * 그대로 검증할 수 있다.
 */
class MediaServiceTest {

    private static final long IMAGE_MAX = 10 * 1024 * 1024L;
    private static final long VIDEO_MAX = 200 * 1024 * 1024L;
    private static final int MAX_FILES = 3;
    private static final Long UPLOADER_ID = 1L;

    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MediaStorageService storageService = mock(MediaStorageService.class);

    private final StorageProperties storageProperties = new StorageProperties(
            new StorageProperties.S3("bucket", "ap-northeast-2", 10, 60),
            new StorageProperties.Limits(IMAGE_MAX, VIDEO_MAX, 180, MAX_FILES));

    private final MediaService mediaService = new MediaService(
            mediaRepository, userRepository,
            new ContentTypePolicy(storageProperties), new MediaKeyGenerator(), storageService,
            new ExifReader(), new ThumbnailGenerator(), storageProperties, new MediaMapper(storageService));

    private final AtomicLong nextId = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        User uploader = User.builder().nickname("여행자").build();
        given(userRepository.getReferenceById(UPLOADER_ID)).willReturn(uploader);

        // IDENTITY 전략이 실제 DB에서 하는 일을 흉내낸다 — 목 리포지토리는 id를 채워주지 않는다
        given(mediaRepository.saveAll(anyList())).willAnswer(invocation -> {
            List<Media> saved = invocation.getArgument(0);
            saved.forEach(m -> ReflectionTestUtils.setField(m, "id", nextId.getAndIncrement()));
            return saved;
        });

        given(storageService.presignDownload(anyString()))
                .willAnswer(invocation -> "https://cdn.example.com/" + invocation.getArgument(0));
    }

    // ── issueUploadUrls ──────────────────────────────────────────

    @Test
    @DisplayName("최대 개수를 넘으면 INVALID_REQUEST — 아무것도 저장하지 않는다")
    void 개수_초과() {
        List<MediaUploadRequest> files = List.of(
                imageRequest(), imageRequest(), imageRequest(), imageRequest());

        assertThatThrownBy(() -> mediaService.issueUploadUrls(UPLOADER_ID, files))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(mediaRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("지원하지 않는 형식이 하나라도 있으면 전부 저장하지 않는다")
    void 지원하지_않는_형식이_섞이면_아무것도_저장하지_않는다() {
        List<MediaUploadRequest> files = List.of(
                imageRequest(),
                new MediaUploadRequest("a.gif", "image/gif", 1000L, null, null, null));

        assertThatThrownBy(() -> mediaService.issueUploadUrls(UPLOADER_ID, files))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_MEDIA_FORMAT);
        verify(mediaRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("영상인데 width·height·durationMs 중 하나라도 없으면 INVALID_REQUEST")
    void 영상_필수값_누락() {
        MediaUploadRequest incomplete =
                new MediaUploadRequest("a.mp4", "video/mp4", 1000L, 1920, 1080, null);

        assertThatThrownBy(() -> mediaService.issueUploadUrls(UPLOADER_ID, List.of(incomplete)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("사진이 상한을 넘으면 MEDIA_TOO_LARGE")
    void 사진_용량_초과() {
        MediaUploadRequest tooLarge =
                new MediaUploadRequest("a.jpg", "image/jpeg", IMAGE_MAX + 1, null, null, null);

        assertThatThrownBy(() -> mediaService.issueUploadUrls(UPLOADER_ID, List.of(tooLarge)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEDIA_TOO_LARGE);
    }

    @Test
    @DisplayName("정상 사진 요청은 PENDING으로 저장되고 업로드 URL을 받는다")
    void 정상_사진_요청() {
        given(storageService.presignUpload(anyString(), eq("image/jpeg"), anyLong()))
                .willReturn(new MediaStorageService.UploadTicket(
                        "https://upload.example.com/x", Instant.now().plusSeconds(600)));

        List<MediaUploadTicket> tickets =
                mediaService.issueUploadUrls(UPLOADER_ID, List.of(imageRequest()));

        assertThat(tickets).hasSize(1);
        assertThat(tickets.get(0).mediaId()).isEqualTo(100L);
        assertThat(tickets.get(0).uploadUrl()).isEqualTo("https://upload.example.com/x");
        verify(storageService).presignUpload(matches("media/1/.+\\.jpg"), eq("image/jpeg"), eq(1000L));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("영상은 발급 시점에 받은 해상도·길이를 그대로 저장한다")
    void 영상_해상도와_길이를_그대로_저장한다() {
        given(storageService.presignUpload(anyString(), eq("video/mp4"), anyLong()))
                .willReturn(new MediaStorageService.UploadTicket("https://upload", Instant.now()));
        MediaUploadRequest video =
                new MediaUploadRequest("a.mp4", "video/mp4", 1000L, 1920, 1080, 15000);

        mediaService.issueUploadUrls(UPLOADER_ID, List.of(video));

        ArgumentCaptor<List<Media>> captor = ArgumentCaptor.forClass(List.class);
        verify(mediaRepository).saveAll(captor.capture());
        Media saved = captor.getValue().get(0);
        assertThat(saved.getWidth()).isEqualTo(1920);
        assertThat(saved.getHeight()).isEqualTo(1080);
        assertThat(saved.getDurationMs()).isEqualTo(15000);
        assertThat(saved.getType()).isEqualTo(MediaType.VIDEO);
    }

    @Test
    @DisplayName("여러 건을 보내면 응답 순서가 요청 순서와 같다")
    void 응답_순서가_요청_순서와_같다() {
        given(storageService.presignUpload(anyString(), anyString(), anyLong()))
                .willReturn(new MediaStorageService.UploadTicket("https://upload", Instant.now()));

        List<MediaUploadTicket> tickets = mediaService.issueUploadUrls(
                UPLOADER_ID, List.of(imageRequest(), imageRequest()));

        assertThat(tickets).extracting(MediaUploadTicket::mediaId)
                .containsExactly(100L, 101L);
    }

    // ── completeUpload ───────────────────────────────────────────

    @Test
    @DisplayName("최대 개수를 넘으면 INVALID_REQUEST")
    void 완료_개수_초과() {
        List<Long> ids = List.of(1L, 2L, 3L, 4L);

        assertThatThrownBy(() -> mediaService.completeUpload(UPLOADER_ID, ids))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("존재하지 않는 mediaId가 섞이면 MEDIA_NOT_FOUND")
    void 존재하지_않는_ID() {
        given(mediaRepository.findAllById(anyList())).willReturn(List.of());

        assertThatThrownBy(() -> mediaService.completeUpload(UPLOADER_ID, List.of(999L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEDIA_NOT_FOUND);
    }

    @Test
    @DisplayName("남이 발급받은 mediaId면 NOT_RESOURCE_OWNER")
    void 소유자가_아니면_거절() {
        Media media = pendingImage(10L, 2L);
        given(mediaRepository.findAllById(anyList())).willReturn(List.of(media));

        assertThatThrownBy(() -> mediaService.completeUpload(UPLOADER_ID, List.of(10L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("이미 READY인 mediaId면 DUPLICATE_REQUEST")
    void 이미_완료된_요청() {
        Media media = pendingImage(10L, UPLOADER_ID);
        media.complete(null, null, null, null, null, null);
        given(mediaRepository.findAllById(anyList())).willReturn(List.of(media));

        assertThatThrownBy(() -> mediaService.completeUpload(UPLOADER_ID, List.of(10L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_REQUEST);
    }

    @Test
    @DisplayName("스토리지에 실제 업로드가 안 됐으면 MEDIA_UPLOAD_INCOMPLETE — 실패 항목을 details에 담는다")
    void 업로드가_끝나지_않음() {
        Media media = pendingImage(10L, UPLOADER_ID);
        given(mediaRepository.findAllById(anyList())).willReturn(List.of(media));
        given(storageService.exists(anyString())).willReturn(false);

        assertThatThrownBy(() -> mediaService.completeUpload(UPLOADER_ID, List.of(10L)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.MEDIA_UPLOAD_INCOMPLETE);
                    assertThat(be.getDetails()).containsEntry("mediaIds", List.of(10L));
                });
    }

    @Test
    @DisplayName("정상 사진 완료 — EXIF 없이도 썸네일을 만들고 READY가 된다")
    void 정상_사진_완료() throws IOException {
        Media media = pendingImage(10L, UPLOADER_ID);
        given(mediaRepository.findAllById(anyList())).willReturn(List.of(media));
        given(storageService.exists(anyString())).willReturn(true);
        given(storageService.download(media.getStorageKey())).willReturn(plainJpeg());

        List<MediaResponse> responses = mediaService.completeUpload(UPLOADER_ID, List.of(10L));

        assertThat(media.getStatus()).isEqualTo(MediaStatus.READY);
        assertThat(media.getThumbnailKey()).isNotNull();
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).url()).isEqualTo("https://cdn.example.com/" + media.getStorageKey());
        assertThat(responses.get(0).thumbnailUrl())
                .isEqualTo("https://cdn.example.com/" + media.getThumbnailKey());
        verify(storageService).upload(eq(media.getThumbnailKey()), any(), eq("image/jpeg"));
    }

    @Test
    @DisplayName("영상 완료는 EXIF를 읽지 않고 발급 시점 값을 그대로 유지한다")
    void 정상_영상_완료() {
        Media media = pendingVideo(11L, UPLOADER_ID, 1920, 1080, 15000);
        given(mediaRepository.findAllById(anyList())).willReturn(List.of(media));
        given(storageService.exists(anyString())).willReturn(true);

        mediaService.completeUpload(UPLOADER_ID, List.of(11L));

        assertThat(media.getStatus()).isEqualTo(MediaStatus.READY);
        assertThat(media.getThumbnailKey()).isNull();
        assertThat(media.getWidth()).isEqualTo(1920);
        assertThat(media.getHeight()).isEqualTo(1080);
        assertThat(media.getDurationMs()).isEqualTo(15000);
        verify(storageService, never()).download(anyString());
    }

    @Test
    @DisplayName("썸네일 생성이 실패해도 나머지는 정상 진행한다")
    void 썸네일_생성_실패해도_진행한다() {
        Media media = pendingImage(10L, UPLOADER_ID);
        given(mediaRepository.findAllById(anyList())).willReturn(List.of(media));
        given(storageService.exists(anyString())).willReturn(true);
        // 유효한 이미지가 아니라 Thumbnailator가 실패한다
        given(storageService.download(media.getStorageKey())).willReturn("not-an-image".getBytes());

        List<MediaResponse> responses = mediaService.completeUpload(UPLOADER_ID, List.of(10L));

        assertThat(media.getStatus()).isEqualTo(MediaStatus.READY);
        assertThat(media.getThumbnailKey()).isNull();
        assertThat(responses.get(0).thumbnailUrl()).isNull();
    }

    // ── 픽스처 ───────────────────────────────────────────────────

    private MediaUploadRequest imageRequest() {
        return new MediaUploadRequest("a.jpg", "image/jpeg", 1000L, null, null, null);
    }

    private Media pendingImage(Long id, Long uploaderId) {
        return media(id, uploaderId, MediaType.IMAGE, null, null, null);
    }

    private Media pendingVideo(Long id, Long uploaderId, Integer width, Integer height, Integer durationMs) {
        return media(id, uploaderId, MediaType.VIDEO, width, height, durationMs);
    }

    private Media media(Long id, Long uploaderId, MediaType type,
                        Integer width, Integer height, Integer durationMs) {
        User uploader = User.builder().nickname("업로더" + uploaderId).build();
        ReflectionTestUtils.setField(uploader, "id", uploaderId);
        Media media = Media.builder()
                .uploader(uploader)
                .type(type)
                .storageKey("media/%d/%d.%s".formatted(uploaderId, id, type == MediaType.IMAGE ? "jpg" : "mp4"))
                .sizeBytes(1000L)
                .width(width)
                .height(height)
                .durationMs(durationMs)
                .build();
        ReflectionTestUtils.setField(media, "id", id);
        return media;
    }

    private byte[] plainJpeg() throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
