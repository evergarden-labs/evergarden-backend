package com.evergarden.evergardenbackend.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시/도 · 시군구 마스터.
 *
 * <p>콘텐츠랩 코드를 자체 코드로 바꾸지 않고 그대로 기본키로 쓴다(ADR-004).
 * 관광지 조회 응답의 areacode를 매핑 없이 바로 조인할 수 있어야 해서다.
 * 그래서 이 엔티티만 {@code Long} 대신 {@code String} 식별자를 쓴다.
 */
@Entity
@Getter
@Table(name = "regions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region {

    @Id
    @Column(length = 10)
    private String code;

    /** 시군구면 상위 시/도. 시/도는 {@code null} */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_code")
    private Region parent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RegionLevel level;

    @Column(nullable = false, length = 50)
    private String name;

    /** 지도 이동과 근사 판정용 중심 좌표. PostGIS를 쓰지 않는다(ADR-027) */
    @Column(name = "center_lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal centerLat;

    @Column(name = "center_lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal centerLng;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @Builder
    private Region(String code, Region parent, RegionLevel level, String name,
                   BigDecimal centerLat, BigDecimal centerLng, LocalDateTime syncedAt) {
        this.code = code;
        this.parent = parent;
        this.level = level;
        this.name = name;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
        this.syncedAt = syncedAt;
    }

    /** 콘텐츠랩에서 다시 받아 갱신한다. 코드는 바꾸지 않는다. */
    public void sync(String name, BigDecimal centerLat, BigDecimal centerLng, LocalDateTime syncedAt) {
        this.name = name;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
        this.syncedAt = syncedAt;
    }
}
