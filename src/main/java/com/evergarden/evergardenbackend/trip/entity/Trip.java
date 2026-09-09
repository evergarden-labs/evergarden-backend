package com.evergarden.evergardenbackend.trip.entity;

import com.evergarden.evergardenbackend.global.entity.BaseTimeEntity;
import com.evergarden.evergardenbackend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여행 일정. 커뮤니티에 공유하면 이것이 "코스"가 된다.
 *
 * <p>아카이브와 1:1로 이어지지만 서로를 필요로 하지 않는다.
 * 연결은 {@code archives.trip_id}가 들고 있어 이 엔티티에는 참조가 없다(ADR-001).
 */
@Entity
@Getter
@Table(name = "trips")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 남의 코스를 가져와 만든 일정이면 원본. 복제 계보를 남긴다(ADR-005) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_trip_id")
    private Trip originTrip;

    @Builder
    private Trip(User owner, String title, LocalDate startDate, LocalDate endDate, Trip originTrip) {
        this.owner = owner;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.originTrip = originTrip;
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    /**
     * 여행 기간을 바꾼다.
     *
     * <p>기간을 줄여 갈 곳 없는 장소가 생기면 서비스가 먼저 막는다(ADR-041).
     * 서버가 말없이 지우거나 옮기지 않는다.
     */
    public void updatePeriod(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** 여행 일수. 1일차부터 이 값까지가 유효한 {@code dayNumber}다. */
    public int durationDays() {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    public boolean isOwnedBy(Long userId) {
        return owner.getId().equals(userId);
    }
}
