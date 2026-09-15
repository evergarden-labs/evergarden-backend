package com.evergarden.evergardenbackend.media.service;

import com.evergarden.evergardenbackend.media.dto.MediaResponse;
import com.evergarden.evergardenbackend.media.entity.Media;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link Media} 응답 변환을 이 클래스 하나로 모은다. {@code url}·{@code thumbnailUrl}이
 * 조회 시점에 발급하는 presigned GET이라(ADR-056), 아카이브처럼 미디어를 함께 반환하는
 * 다른 도메인도 이 변환을 그대로 재사용해야 presign 로직이 두 곳에 흩어지지 않는다.
 */
@Component
@RequiredArgsConstructor
public class MediaMapper {

    private final MediaStorageService storageService;

    public MediaResponse toResponse(Media media) {
        String url = storageService.presignDownload(media.getStorageKey());
        String thumbnailUrl = media.getThumbnailKey() != null
                ? storageService.presignDownload(media.getThumbnailKey())
                : null;
        return MediaResponse.of(media, url, thumbnailUrl);
    }
}
