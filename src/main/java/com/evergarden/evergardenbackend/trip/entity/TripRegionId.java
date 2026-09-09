package com.evergarden.evergardenbackend.trip.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@link TripRegion}의 복합키. 일정 하나에 같은 지역이 두 번 붙지 않게 막는다. */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripRegionId implements Serializable {

    @Column(name = "trip_id")
    private Long tripId;

    @Column(name = "region_code", length = 10)
    private String regionCode;

    public TripRegionId(Long tripId, String regionCode) {
        this.tripId = tripId;
        this.regionCode = regionCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TripRegionId that)) return false;
        return Objects.equals(tripId, that.tripId) && Objects.equals(regionCode, that.regionCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tripId, regionCode);
    }
}
