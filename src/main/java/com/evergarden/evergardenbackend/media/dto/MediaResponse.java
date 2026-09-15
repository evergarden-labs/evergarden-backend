package com.evergarden.evergardenbackend.media.dto;

import com.evergarden.evergardenbackend.media.entity.Media;
import com.evergarden.evergardenbackend.media.entity.MediaStatus;
import com.evergarden.evergardenbackend.media.entity.MediaType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 명세의 {@code Media} 스키마. {@code url}·{@code thumbnailUrl}은 엔티티에 저장된 값이 아니라
 * 조회 시점에 발급한 presigned GET URL이다(ADR-056) — 그래서 엔티티가 아니라
 * 이 값들을 인자로 받는 {@link #of}로만 만든다.
 */
public record MediaResponse(
        Long mediaId,
        MediaStatus status,
        MediaType type,
        String url,
        String thumbnailUrl,
        Integer width,
        Integer height,
        Integer durationMs,
        Long sizeBytes,
        LocalDateTime takenAt,
        BigDecimal lat,
        BigDecimal lng,
        LocalDateTime createdAt) {

    public static MediaResponse of(Media media, String url, String thumbnailUrl) {
        return new MediaResponse(
                media.getId(), media.getStatus(), media.getType(), url, thumbnailUrl,
                media.getWidth(), media.getHeight(), media.getDurationMs(), media.getSizeBytes(),
                media.getTakenAt(), media.getLat(), media.getLng(), media.getCreatedAt());
    }
}
