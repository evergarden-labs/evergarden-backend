package com.evergarden.evergardenbackend.archive.entity;

import com.evergarden.evergardenbackend.global.entity.BaseTimeEntity;
import com.evergarden.evergardenbackend.media.entity.Media;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 아카이브에 배치된 사진·영상 한 건.
 *
 * <p>같은 사진을 한 아카이브에 두 번 배치할 수 있어서, 사진 자체({@link Media})와
 * "배치된 한 건"을 구분한다. 명세의 {@code itemId}가 이 엔티티의 식별자다.
 */
@Entity
@Getter
@Table(name = "archive_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArchiveItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "archive_id", nullable = false)
    private Archive archive;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    /** 캔버스 위 배치. JSONB로 통째 저장한다 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private ArchiveLayout layout;

    /** 사진에 붙인 짧은 글(ADR-029) */
    @Column(length = 300)
    private String caption;

    @Builder
    private ArchiveItem(Archive archive, Media media, short sortOrder,
                        ArchiveLayout layout, String caption) {
        this.archive = archive;
        this.media = media;
        this.sortOrder = sortOrder;
        this.layout = layout;
        this.caption = caption;
    }

    /** 항목 하나의 배치·순서·캡션을 바꾼다. 실시간 편집의 변경 단위이기도 하다(ADR-051). */
    public void update(Short sortOrder, ArchiveLayout layout, String caption) {
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
        if (layout != null) {
            this.layout = layout;
        }
        if (caption != null) {
            this.caption = caption;
        }
    }

    public void reorder(short sortOrder, ArchiveLayout layout) {
        this.sortOrder = sortOrder;
        this.layout = layout;
    }
}
