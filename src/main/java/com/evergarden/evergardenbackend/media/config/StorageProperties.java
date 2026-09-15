package com.evergarden.evergardenbackend.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code storage.*} 설정값(ADR-023 · ADR-056).
 *
 * @param s3     버킷·리전과 presigned URL 만료 시간
 * @param limits 업로드 상한값(ADR-015)
 */
@ConfigurationProperties("storage")
public record StorageProperties(S3 s3, Limits limits) {

    /**
     * @param bucket                          비공개 버킷. presigned URL로만 접근한다
     * @param region                          버킷 리전
     * @param presignedUploadUrlExpiryMinutes 업로드(PUT) URL 만료 — 짧게 잡아 발급 즉시 쓰게 한다
     * @param presignedDownloadUrlExpiryMinutes 조회(GET) URL 만료 — 화면 하나 보는 동안 끊기지 않을 만큼 길게 잡는다(ADR-056)
     */
    public record S3(
            String bucket,
            String region,
            long presignedUploadUrlExpiryMinutes,
            long presignedDownloadUrlExpiryMinutes) {
    }

    /**
     * @param imageMaxBytes         사진 상한(10MB)
     * @param videoMaxBytes         영상 상한(200MB)
     * @param videoMaxDurationSeconds 영상 길이 상한. {@code durationMs}는 앱이 보낸 값이라 서버가 검증하지 못해
     *                                실질적으로는 {@code videoMaxBytes}가 상한 역할을 한다(ADR-052)
     * @param maxFilesPerRequest    한 번에 발급·완료할 수 있는 최대 개수
     */
    public record Limits(
            long imageMaxBytes,
            long videoMaxBytes,
            int videoMaxDurationSeconds,
            int maxFilesPerRequest) {
    }
}
