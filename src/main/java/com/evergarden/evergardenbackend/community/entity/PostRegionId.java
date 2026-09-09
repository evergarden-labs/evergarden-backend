package com.evergarden.evergardenbackend.community.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@link PostRegion}의 복합키. */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostRegionId implements Serializable {

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "region_code", length = 10)
    private String regionCode;

    public PostRegionId(Long postId, String regionCode) {
        this.postId = postId;
        this.regionCode = regionCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostRegionId that)) return false;
        return Objects.equals(postId, that.postId) && Objects.equals(regionCode, that.regionCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, regionCode);
    }
}
