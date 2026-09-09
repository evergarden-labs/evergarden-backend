package com.evergarden.evergardenbackend.timecapsule.entity;

import com.evergarden.evergardenbackend.media.entity.Media;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 캡슐에 담은 사진.
 *
 * <p>캡슐을 지우면 이 연결도 함께 사라지지만 {@link Media} 자체는 남는다.
 * 같은 사진을 아카이브에서도 쓸 수 있어서다.
 */
@Entity
@Getter
@Table(name = "time_capsule_media")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimeCapsuleMedia {

    @EmbeddedId
    private TimeCapsuleMediaId id;

    @MapsId("capsuleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "capsule_id", nullable = false)
    private TimeCapsule capsule;

    @MapsId("mediaId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    public TimeCapsuleMedia(TimeCapsule capsule, Media media, short sortOrder) {
        this.id = new TimeCapsuleMediaId(capsule.getId(), media.getId());
        this.capsule = capsule;
        this.media = media;
        this.sortOrder = sortOrder;
    }
}
