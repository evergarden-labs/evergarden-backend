package com.evergarden.evergardenbackend.map.entity;

import com.evergarden.evergardenbackend.place.entity.Region;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

/**
 * 지역 방문 인증 기록(MAP-01).
 *
 * <p>유니크 제약이 없다. 같은 지역을 여러 번 인증할 수 있어야 정원이 자란다(ADR-038).
 * 7일 쿨다운은 인증 자체를 막지 않고 보상만 건너뛴다.
 *
 * <p>인증 시점의 좌표와 정확도를 남기는 이유는 어뷰징을 조사할 근거가 필요해서다.
 * 다만 정확도 값도 단말이 보내는 것이라 조작을 막지는 못한다(ADR-016).
 */
@Entity
@Getter
@Table(name = "region_visits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_code", nullable = false)
    private Region region;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal lng;

    /** 단말이 좌표와 함께 보낸 정확도(m). 임계값을 넘으면 서비스가 인증을 거절한다 */
    @Column(name = "accuracy_meters", precision = 6, scale = 1)
    private BigDecimal accuracyMeters;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private RegionVisit(User user, Region region, BigDecimal lat, BigDecimal lng,
                        BigDecimal accuracyMeters, LocalDateTime verifiedAt) {
        this.user = user;
        this.region = region;
        this.lat = lat;
        this.lng = lng;
        this.accuracyMeters = accuracyMeters;
        this.verifiedAt = verifiedAt;
    }
}
