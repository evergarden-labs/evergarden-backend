package com.evergarden.evergardenbackend.media.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** S3 오브젝트 키 규칙(ADR-055) — {@code media/{userId}/{uuid}.{ext}}, 썸네일은 같은 이름에 접미사만 붙인다. */
@Component
public class MediaKeyGenerator {

    private static final String THUMBNAIL_SUFFIX = "_thumb.jpg";

    public String originalKey(Long userId, String extension) {
        return "media/%d/%s.%s".formatted(userId, UUID.randomUUID(), extension);
    }

    public String thumbnailKeyFor(String originalKey) {
        int dot = originalKey.lastIndexOf('.');
        String base = dot >= 0 ? originalKey.substring(0, dot) : originalKey;
        return base + THUMBNAIL_SUFFIX;
    }
}
