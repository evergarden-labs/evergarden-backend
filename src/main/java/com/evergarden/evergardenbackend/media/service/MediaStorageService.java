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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * S3 접근을 이 클래스 하나로 모은다(ADR-023 · ADR-056). 다른 클래스는 SDK를 직접 부르지 않는다 —
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

    /**
     * 업로드(PUT) URL을 발급한다. 앱이 이 주소에 파일을 직접 올린다.
     *
     * <p>{@code contentLength}를 서명에 포함시킨다 — 이걸 빼면 발급 시점에 검증한
     * {@code sizeBytes} 상한(ADR-015)이 껍데기만 남는다. S3는 서명에 없는 값이면
     * 클라이언트가 실제로 얼마를 보내든 막지 않아서, 앱이 선언과 다른 크기를 올려도 통과된다.
     * 서명에 넣어두면 실제 {@code Content-Length}가 다를 때 S3가 서명 불일치로 거절한다.
     */
    public UploadTicket presignUpload(String key, String contentType, long sizeBytes) {
        Duration ttl = Duration.ofMinutes(storageProperties.s3().presignedUploadUrlExpiryMinutes());
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(storageProperties.s3().bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(b -> b
                .signatureDuration(ttl)
                .putObjectRequest(putRequest));
        return new UploadTicket(presigned.url().toString(), Instant.now().plus(ttl));
    }

    /** 조회(GET) URL을 발급한다(ADR-056). 버킷이 비공개라 조회할 때마다 새로 만든다. */
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

    /**
     * 앱이 presigned URL로 실제로 업로드를 마쳤는지 확인한다.
     *
     * <p><b>404뿐 아니라 403도 "없음"으로 본다.</b> IAM 정책이 {@code s3:ListBucket}을
     * 주지 않으면(최소 권한 원칙상 일부러 안 줌) S3가 없는 객체에 404 대신 403을 돌려준다 —
     * 객체 존재 여부 자체를 숨기는 S3의 의도된 동작이다. 이 버킷은 같은 자격증명으로만
     * 읽고 쓰므로 403이 "진짜 권한 없음"일 상황이 없어, 안전하게 "없음"으로 간주한다.
     */
    public boolean exists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(storageProperties.s3().bucket())
                    .key(key)
                    .build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404 || e.statusCode() == 403) {
                return false;
            }
            throw e;
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
