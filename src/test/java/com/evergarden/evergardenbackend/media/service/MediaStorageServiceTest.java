package com.evergarden.evergardenbackend.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.evergarden.evergardenbackend.media.config.StorageProperties;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3 경계에서 생기는 실제 동작(존재 확인의 403/404, 업로드 상한 서명)을 확인한다.
 *
 * <p>아카이브 백엔드 개발 중 실제 S3로 재현해서 잡은 버그 — {@code s3:ListBucket} 권한이
 * 없으면 없는 객체에 404가 아니라 403이 돌아온다는 걸 놓쳐서 {@code MEDIA_UPLOAD_INCOMPLETE}
 * 경로가 항상 500으로 떨어졌다. 회귀를 막기 위한 테스트다.
 */
class MediaStorageServiceTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final S3Presigner s3Presigner = mock(S3Presigner.class);
    private final StorageProperties storageProperties = new StorageProperties(
            new StorageProperties.S3("bucket", "ap-northeast-2", 10, 60), null);

    private final MediaStorageService storageService =
            new MediaStorageService(s3Client, s3Presigner, storageProperties);

    @Test
    @DisplayName("객체가 있으면 true")
    void 존재함() {
        given(s3Client.headObject(any(HeadObjectRequest.class))).willReturn(null);

        assertThat(storageService.exists("key")).isTrue();
    }

    @Test
    @DisplayName("404면 없음으로 본다")
    void 없음_404() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(404).build());

        assertThat(storageService.exists("key")).isFalse();
    }

    @Test
    @DisplayName("403도 없음으로 본다 — s3:ListBucket이 없으면 S3가 404 대신 403을 준다")
    void 없음_403() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(403).build());

        assertThat(storageService.exists("key")).isFalse();
    }

    @Test
    @DisplayName("403·404가 아닌 오류는 그대로 던진다 — 진짜 장애를 없음으로 감추지 않는다")
    void 다른_오류는_전파() {
        S3Exception serverError = (S3Exception) S3Exception.builder().statusCode(500).build();
        given(s3Client.headObject(any(HeadObjectRequest.class))).willThrow(serverError);

        assertThatThrownBy(() -> storageService.exists("key")).isSameAs(serverError);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("업로드 URL 서명에 선언한 크기를 포함한다 — 안 넣으면 상한 검증이 껍데기만 남는다")
    void 업로드_URL이_크기를_서명에_포함한다() throws MalformedURLException {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://example.com/x").toURL());
        given(s3Presigner.presignPutObject(any(Consumer.class))).willReturn(presigned);

        storageService.presignUpload("key", "image/jpeg", 12345L);

        var captor = org.mockito.ArgumentCaptor.forClass(Consumer.class);
        org.mockito.Mockito.verify(s3Presigner).presignPutObject((Consumer<PutObjectPresignRequest.Builder>) captor.capture());
        PutObjectPresignRequest.Builder builder = PutObjectPresignRequest.builder();
        ((Consumer<PutObjectPresignRequest.Builder>) captor.getValue()).accept(builder);

        assertThat(builder.build().putObjectRequest().contentLength()).isEqualTo(12345L);
    }
}
