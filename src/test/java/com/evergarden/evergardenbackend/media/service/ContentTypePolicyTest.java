package com.evergarden.evergardenbackend.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.media.config.StorageProperties;
import com.evergarden.evergardenbackend.media.entity.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 형식·용량 검증(ADR-015 · ADR-023). */
class ContentTypePolicyTest {

    private static final long IMAGE_MAX = 10 * 1024 * 1024L;
    private static final long VIDEO_MAX = 200 * 1024 * 1024L;

    private ContentTypePolicy policy;

    @BeforeEach
    void setUp() {
        StorageProperties.Limits limits = new StorageProperties.Limits(IMAGE_MAX, VIDEO_MAX, 180, 20);
        StorageProperties properties = new StorageProperties(null, limits);
        policy = new ContentTypePolicy(properties);
    }

    @ParameterizedTest
    @CsvSource({
            "image/jpeg, IMAGE, jpg",
            "image/png, IMAGE, png",
            "image/heic, IMAGE, heic",
            "image/webp, IMAGE, webp",
            "video/mp4, VIDEO, mp4",
            "video/quicktime, VIDEO, mov",
    })
    @DisplayName("지원하는 형식은 종류와 확장자를 돌려준다")
    void 지원하는_형식(String contentType, MediaType expectedType, String expectedExtension) {
        assertThat(policy.typeOf(contentType)).isEqualTo(expectedType);
        assertThat(policy.extensionOf(contentType)).isEqualTo(expectedExtension);
    }

    @Test
    @DisplayName("지원하지 않는 형식은 INVALID_MEDIA_FORMAT")
    void 지원하지_않는_형식() {
        assertThatThrownBy(() -> policy.typeOf("image/gif"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_MEDIA_FORMAT);
    }

    @Test
    @DisplayName("사진이 10MB를 넘으면 MEDIA_TOO_LARGE")
    void 사진_용량_초과() {
        assertThatThrownBy(() -> policy.checkSize(MediaType.IMAGE, IMAGE_MAX + 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEDIA_TOO_LARGE);
    }

    @Test
    @DisplayName("사진이 상한 이하면 통과한다")
    void 사진_용량_통과() {
        policy.checkSize(MediaType.IMAGE, IMAGE_MAX);
    }

    @Test
    @DisplayName("영상이 200MB를 넘으면 MEDIA_TOO_LARGE")
    void 영상_용량_초과() {
        assertThatThrownBy(() -> policy.checkSize(MediaType.VIDEO, VIDEO_MAX + 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEDIA_TOO_LARGE);
    }
}
