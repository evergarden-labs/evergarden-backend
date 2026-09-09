package com.evergarden.evergardenbackend.trip.entity;

import com.evergarden.evergardenbackend.global.entity.BaseTimeEntity;
import com.evergarden.evergardenbackend.place.entity.Place;
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

/**
 * 일정에 담긴 장소 한 건.
 *
 * <p>같은 카페를 1일차와 3일차에 각각 담을 수 있어서, 관광지 자체({@link Place})와
 * "일정에 담긴 한 건"을 구분한다. 명세의 {@code tripPlaceId}가 이 엔티티의 식별자다.
 */
@Entity
@Getter
@Table(name = "trip_places")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripPlace extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    /** 여행 1일차 = 1 */
    @Column(name = "day_number", nullable = false)
    private short dayNumber;

    /** 그 날의 방문 순서. 동선이 여기서 나온다 */
    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(columnDefinition = "text")
    private String memo;

    @Builder
    private TripPlace(Trip trip, Place place, short dayNumber, short sortOrder, String memo) {
        this.trip = trip;
        this.place = place;
        this.dayNumber = dayNumber;
        this.sortOrder = sortOrder;
        this.memo = memo;
    }

    /** 드래그로 순서를 바꾸거나 다른 날로 옮긴다(PLAN-02 · PLAN-04). */
    public void relocate(short dayNumber, short sortOrder) {
        this.dayNumber = dayNumber;
        this.sortOrder = sortOrder;
    }

    public void updateMemo(String memo) {
        this.memo = memo;
    }
}
