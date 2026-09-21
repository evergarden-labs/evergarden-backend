package com.evergarden.evergardenbackend.community.dto;

import com.evergarden.evergardenbackend.archive.dto.ArchiveSummary;
import com.evergarden.evergardenbackend.community.entity.ShareType;
import com.evergarden.evergardenbackend.place.dto.RegionSummary;
import com.evergarden.evergardenbackend.trip.dto.TripSummary;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 명세의 {@code PostDetail} 스키마 — {@code PostSummary}에 {@code shareType}·
 * {@code sharedCourse}·{@code sharedArchive}·{@code deletedShare}·{@code updatedAt}을
 * 더한 모양이다. 트립·아카이브 쪽과 같은 이유로 상속 대신 필드를 그대로 펼친다.
 */
public record PostDetail(
        Long postId,
        Author author,
        String content,
        String thumbnailUrl,
        int likeCount,
        int commentCount,
        boolean likedByMe,
        List<RegionSummary> regions,
        LocalDateTime createdAt,
        ShareType shareType,
        TripSummary sharedCourse,
        ArchiveSummary sharedArchive,
        List<ShareType> deletedShare,
        LocalDateTime updatedAt) {
}
