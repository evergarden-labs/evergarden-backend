package com.evergarden.evergardenbackend.media.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 클라이언트 설정.
 *
 * <p>자격증명은 SDK 기본 체인({@link DefaultCredentialsProvider})을 쓴다 — 로컬은
 * {@code ~/.aws/credentials}, 배포 환경은 인스턴스 역할이나 환경변수를 순서대로 찾는다.
 * 코드에 키를 넣지 않는다.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
@RequiredArgsConstructor
public class S3Config {

    private final StorageProperties storageProperties;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(storageProperties.s3().region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(storageProperties.s3().region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
