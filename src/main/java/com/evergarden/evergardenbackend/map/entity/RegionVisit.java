package com.evergarden.evergardenbackend.map.entity;

import com.evergarden.evergardenbackend.garden.entity.GardenObject;
import com.evergarden.evergardenbackend.garden.entity.RewardStatus;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 지역 방문 인증 기록(MAP-01).
 *
 * <p>유니크 제약이 없다. 같은 지역을 여러 번 인증할 수 있어야 정원이 자란다(ADR-038).
 * 7일 쿨다운은 인증 자체를 막지 않고 보상만 건너뛴다.
 *
 * <p>인증 시점의 좌표와 정확도를 남기는 이유는 어뷰징을 조사할 근거가 필요해서다.
 * 다만 정확도 값도 단말이 보내는 것이라 조작을 막지는 못한다(ADR-016).
 *
 * <p>{@code BaseTimeEntity}를 상속하지 않고 {@code createdAt}만 직접 두지만,
 * {@code @CreatedDate}가 실제로 채워지려면 {@link AuditingEntityListener}가
 * 있어야 한다 — 빠지면 항상 null로 저장을 시도해 {@code NOT NULL} 제약에
 * 매번 걸린다({@code PostLike}에서 실전 확인).
 */
@Entity
@Getter
@Table(name = "region_visits")
@EntityListeners(AuditingEntityListener.class)
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

    /**
     * 이 인증이 정원에 남긴 결과의 스냅샷(GARDEN-02). 인증 시점에 한 번 계산해서
     * 여기 같이 저장한다 — 이후 재방문으로 {@code UserGardenObject}가 더 자라도
     * "그때 이 인증으로 무슨 일이 있었는지"는 안 바뀌어야 {@code getVisitReward}가
     * 다시 봐도 같은 내용을 보여줄 수 있다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "reward_status", nullable = false, length = 10)
    private RewardStatus rewardStatus;

    /** 그 인증으로 해금·성장한 오브젝트. {@code rewardStatus}가 {@code NONE}이면 {@code null} */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "garden_object_id")
    private GardenObject gardenObject;

    @Column(name = "previous_stage")
    private Short previousStage;

    @Column(name = "current_stage")
    private Short currentStage;

    /** {@code rewardStatus}가 {@code COOLDOWN}일 때만 채운다(ADR-018) */
    @Column(name = "next_available_at")
    private LocalDateTime nextAvailableAt;

    @Builder
    private RegionVisit(User user, Region region, BigDecimal lat, BigDecimal lng,
                        BigDecimal accuracyMeters, LocalDateTime verifiedAt,
                        RewardStatus rewardStatus, GardenObject gardenObject,
                        Short previousStage, Short currentStage, LocalDateTime nextAvailableAt) {
        this.user = user;
        this.region = region;
        this.lat = lat;
        this.lng = lng;
        this.accuracyMeters = accuracyMeters;
        this.verifiedAt = verifiedAt;
        this.rewardStatus = rewardStatus;
        this.gardenObject = gardenObject;
        this.previousStage = previousStage;
        this.currentStage = currentStage;
        this.nextAvailableAt = nextAvailableAt;
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
