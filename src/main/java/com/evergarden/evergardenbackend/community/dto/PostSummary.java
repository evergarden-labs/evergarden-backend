package com.evergarden.evergardenbackend.community.dto;

import com.evergarden.evergardenbackend.place.dto.RegionSummary;
import java.time.LocalDateTime;
import java.util.List;

/** 명세의 {@code PostSummary} 스키마. */
public record PostSummary(
        Long postId,
        Author author,
        String content,
        String thumbnailUrl,
        int likeCount,
        int commentCount,
        boolean likedByMe,
        List<RegionSummary> regions,
        LocalDateTime createdAt) {
}
