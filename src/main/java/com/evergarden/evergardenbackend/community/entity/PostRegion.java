package com.evergarden.evergardenbackend.community.entity;

import com.evergarden.evergardenbackend.place.entity.Region;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시물이 걸리는 지역. 등록 시점에 굳힌 스냅샷이다(ADR-003).
 *
 * <p>매번 코스에서 계산하면 원본이 수정될 때 피드 노출이 조용히 바뀌고,
 * 원본이 삭제된 게시물은 지역 피드에서 아예 사라진다.
 */
@Entity
@Getter
@Table(name = "post_regions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostRegion {

    @EmbeddedId
    private PostRegionId id;

    @MapsId("postId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @MapsId("regionCode")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_code", nullable = false)
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RegionSource source;

    public PostRegion(Post post, Region region, RegionSource source) {
        this.id = new PostRegionId(post.getId(), region.getCode());
        this.post = post;
        this.region = region;
        this.source = source;
    }
}
