package com.evergarden.evergardenbackend.media.service;

import com.evergarden.evergardenbackend.media.config.StorageProperties;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * S3 접근을 이 클래스 하나로 모은다(ADR-023 · ADR-055). 다른 클래스는 SDK를 직접 부르지 않는다 —
 * 버킷 이름·리전 같은 세부사항이 여기에만 있으면 나중에 스토리지를 바꿔도 여기만 고치면 된다.
 */
@Component
@RequiredArgsConstructor
public class MediaStorageService {

    public record UploadTicket(String url, Instant expiresAt) {
    }

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;

    /** 업로드(PUT) URL을 발급한다. 앱이 이 주소에 파일을 직접 올린다. */
    public UploadTicket presignUpload(String key, String contentType) {
        Duration ttl = Duration.ofMinutes(storageProperties.s3().presignedUploadUrlExpiryMinutes());
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(storageProperties.s3().bucket())
                .key(key)
                .contentType(contentType)
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(b -> b
                .signatureDuration(ttl)
                .putObjectRequest(putRequest));
        return new UploadTicket(presigned.url().toString(), Instant.now().plus(ttl));
    }

    /** 조회(GET) URL을 발급한다(ADR-055). 버킷이 비공개라 조회할 때마다 새로 만든다. */
    public String presignDownload(String key) {
        Duration ttl = Duration.ofMinutes(storageProperties.s3().presignedDownloadUrlExpiryMinutes());
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(storageProperties.s3().bucket())
                .key(key)
                .build();
        return s3Presigner.presignGetObject(b -> b
                        .signatureDuration(ttl)
                        .getObjectRequest(getRequest))
                .url()
                .toString();
    }

    /** 앱이 presigned URL로 실제로 업로드를 마쳤는지 확인한다. */
    public boolean exists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(storageProperties.s3().bucket())
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    /** EXIF 추출·썸네일 생성을 위해 원본을 내려받는다. */
    public byte[] download(String key) {
        return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(storageProperties.s3().bucket())
                        .key(key)
                        .build())
                .asByteArray();
    }

    /** 서버가 만든 파생물(썸네일)을 올린다. 원본은 앱이 직접 올리므로 이 메서드를 쓰지 않는다. */
    public void upload(String key, byte[] bytes, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(storageProperties.s3().bucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(bytes));
    }
}
