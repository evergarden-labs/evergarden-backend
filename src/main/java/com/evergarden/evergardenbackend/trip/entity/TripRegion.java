package com.evergarden.evergardenbackend.trip.entity;

import com.evergarden.evergardenbackend.place.entity.Region;
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
 * 일정 생성 시 고른 여행지. 복수 선택이 가능해 연결 테이블로 둔다(PLAN-01).
 *
 * <p>여기 담긴 지역과 담긴 장소들의 지역을 합친 것이
 * 게시물 지역 스냅샷의 재료가 된다(ADR-003).
 */
@Entity
@Getter
@Table(name = "trip_regions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripRegion {

    @EmbeddedId
    private TripRegionId id;

    @MapsId("tripId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @MapsId("regionCode")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_code", nullable = false)
    private Region region;

    public TripRegion(Trip trip, Region region) {
        this.id = new TripRegionId(trip.getId(), region.getCode());
        this.trip = trip;
        this.region = region;
    }
}
