package com.evergarden.evergardenbackend.timecapsule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@link TimeCapsuleMedia}의 복합키. 같은 사진을 한 캡슐에 두 번 담지 못하게 한다. */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimeCapsuleMediaId implements Serializable {

    @Column(name = "capsule_id")
    private Long capsuleId;

    @Column(name = "media_id")
    private Long mediaId;

    public TimeCapsuleMediaId(Long capsuleId, Long mediaId) {
        this.capsuleId = capsuleId;
        this.mediaId = mediaId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeCapsuleMediaId that)) return false;
        return Objects.equals(capsuleId, that.capsuleId) && Objects.equals(mediaId, that.mediaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(capsuleId, mediaId);
    }
}
