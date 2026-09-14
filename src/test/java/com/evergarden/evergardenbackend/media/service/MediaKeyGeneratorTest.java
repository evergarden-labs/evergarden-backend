package com.evergarden.evergardenbackend.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** S3 오브젝트 키 규칙(ADR-055). */
class MediaKeyGeneratorTest {

    private final MediaKeyGenerator generator = new MediaKeyGenerator();

    @Test
    @DisplayName("원본 키는 media/{userId}/{uuid}.{ext} 모양이다")
    void 원본_키_모양() {
        String key = generator.originalKey(7L, "jpg");

        assertThat(key).matches(
                "media/7/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg");
    }

    @Test
    @DisplayName("같은 사용자라도 호출마다 다른 키를 만든다")
    void 매번_다른_키() {
        String first = generator.originalKey(7L, "jpg");
        String second = generator.originalKey(7L, "jpg");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("썸네일 키는 확장자를 떼고 _thumb.jpg를 붙인다")
    void 썸네일_키() {
        String original = "media/7/abc-123.heic";

        assertThat(generator.thumbnailKeyFor(original)).isEqualTo("media/7/abc-123_thumb.jpg");
    }

    @Test
    @DisplayName("확장자가 없는 키도 안전하게 처리한다")
    void 확장자_없는_키() {
        assertThat(generator.thumbnailKeyFor("media/7/no-extension"))
                .isEqualTo("media/7/no-extension_thumb.jpg");
    }
}
