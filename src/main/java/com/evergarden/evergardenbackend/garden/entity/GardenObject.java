package com.evergarden.evergardenbackend.garden.entity;

import com.evergarden.evergardenbackend.global.entity.BaseTimeEntity;
import com.evergarden.evergardenbackend.place.entity.Region;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * 식물·오브젝트 도감. 운영자가 채우는 마스터 데이터다.
 *
 * <p>사용자가 실제로 가진 것은 {@link UserGardenObject}에 따로 둔다.
 */
@Entity
@Getter
@Table(name = "garden_objects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GardenObject extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 지역 전용이면 그 지역, 공통이면 {@code null} */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_code")
    private Region region;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GardenObjectType type;

    /** 더 자랄 수 없는 단계 */
    @Column(name = "max_stage", nullable = false)
    private short maxStage;

    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;

    @Builder
    private GardenObject(Region region, String name, GardenObjectType type,
                         short maxStage, String imageUrl) {
        this.region = region;
        this.name = name;
        this.type = type;
        this.maxStage = maxStage;
        this.imageUrl = imageUrl;
    }
}
