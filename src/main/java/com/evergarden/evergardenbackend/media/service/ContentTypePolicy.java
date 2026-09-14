package com.evergarden.evergardenbackend.media.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.media.config.StorageProperties;
import com.evergarden.evergardenbackend.media.entity.MediaType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 업로드 요청의 형식·용량을 검증한다(ADR-015).
 *
 * <p>URL을 발급하기 전에 걸러야 잘못된 파일이 스토리지에 올라가지 않는다(ADR-023).
 */
@Component
@RequiredArgsConstructor
public class ContentTypePolicy {

    private record Entry(MediaType type, String extension) {
    }

    private static final Map<String, Entry> SUPPORTED = Map.of(
            "image/jpeg", new Entry(MediaType.IMAGE, "jpg"),
            "image/png", new Entry(MediaType.IMAGE, "png"),
            "image/heic", new Entry(MediaType.IMAGE, "heic"),
            "image/webp", new Entry(MediaType.IMAGE, "webp"),
            "video/mp4", new Entry(MediaType.VIDEO, "mp4"),
            "video/quicktime", new Entry(MediaType.VIDEO, "mov"));

    private final StorageProperties storageProperties;

    public MediaType typeOf(String contentType) {
        return entryOf(contentType).type();
    }

    public String extensionOf(String contentType) {
        return entryOf(contentType).extension();
    }

    private Entry entryOf(String contentType) {
        Entry entry = SUPPORTED.get(contentType);
        if (entry == null) {
            throw new BusinessException(ErrorCode.INVALID_MEDIA_FORMAT);
        }
        return entry;
    }

    public void checkSize(MediaType type, long sizeBytes) {
        long max = type == MediaType.IMAGE
                ? storageProperties.limits().imageMaxBytes()
                : storageProperties.limits().videoMaxBytes();
        if (sizeBytes > max) {
            throw new BusinessException(ErrorCode.MEDIA_TOO_LARGE);
        }
    }
}
