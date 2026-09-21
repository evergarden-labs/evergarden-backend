package com.evergarden.evergardenbackend.community.service;

import com.evergarden.evergardenbackend.archive.dto.ArchiveSummary;
import com.evergarden.evergardenbackend.community.dto.Author;
import com.evergarden.evergardenbackend.community.dto.PostDetail;
import com.evergarden.evergardenbackend.community.dto.PostSummary;
import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.ShareType;
import com.evergarden.evergardenbackend.place.dto.RegionSummary;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.trip.dto.TripSummary;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** {@link Post} 응답 변환. 목록은 {@link PostSummary}, 상세는 {@link PostDetail}. */
@Component
public class PostMapper {

    public PostSummary toSummary(Post post, List<Region> regions, String thumbnailUrl, boolean likedByMe) {
        return new PostSummary(
                post.getId(), Author.of(post.getAuthor()), post.getContent(), thumbnailUrl,
                post.getLikeCount(), post.getCommentCount(), likedByMe,
                regions.stream().map(RegionSummary::of).toList(), post.getCreatedAt());
    }

    public PostDetail toDetail(Post post, List<Region> regions, String thumbnailUrl, boolean likedByMe,
                               TripSummary sharedCourse, ArchiveSummary sharedArchive) {
        return new PostDetail(
                post.getId(), Author.of(post.getAuthor()), post.getContent(), thumbnailUrl,
                post.getLikeCount(), post.getCommentCount(), likedByMe,
                regions.stream().map(RegionSummary::of).toList(), post.getCreatedAt(),
                post.getShareType(), sharedCourse, sharedArchive, deletedShare(post), post.getUpdatedAt());
    }

    private List<ShareType> deletedShare(Post post) {
        List<ShareType> deleted = new ArrayList<>();
        if (post.isSharedTripMissing()) {
            deleted.add(ShareType.COURSE);
        }
        if (post.isSharedArchiveMissing()) {
            deleted.add(ShareType.ARCHIVE);
        }
        return deleted;
    }
}
