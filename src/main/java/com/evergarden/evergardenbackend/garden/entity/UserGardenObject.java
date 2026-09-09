package com.evergarden.evergardenbackend.garden.entity;

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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 해금하고 키운 식물·오브젝트.
 *
 * <p>정원은 사용자당 하나라 지역 구분이 없다(ADR-037).
 * 어느 지역에서 얻었든 한 정원에 모인다.
 */
@Entity
@Getter
@Table(
        name = "user_garden_objects",
        uniqueConstraints = @UniqueConstraint(
                name = "ugo_uk", columnNames = {"user_id", "garden_object_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserGardenObject extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "garden_object_id", nullable = false)
    private GardenObject gardenObject;

    @Column(nullable = false)
    private short stage;

    @Column(name = "position_x")
    private Short positionX;

    @Column(name = "position_y")
    private Short positionY;

    @Column(name = "unlocked_at", nullable = false)
    private LocalDateTime unlockedAt;

    /** 마지막으로 자란 시각. 7일 쿨다운을 여기서 판정한다(ADR-018) */
    @Column(name = "last_grown_at")
    private LocalDateTime lastGrownAt;

    @Builder
    private UserGardenObject(User user, GardenObject gardenObject, LocalDateTime unlockedAt) {
        this.user = user;
        this.gardenObject = gardenObject;
        this.unlockedAt = unlockedAt;
        this.stage = 1;
    }

    /**
     * 쿨다운이 지났고 아직 최대 단계가 아니면 한 단계 자란다(GARDEN-02).
     *
     * @return 실제로 자랐으면 {@code true}
     */
    public boolean growIfPossible(LocalDateTime now, int cooldownDays) {
        if (!canGrowAt(now, cooldownDays)) {
            return false;
        }
        this.stage++;
        this.lastGrownAt = now;
        return true;
    }

    /** 쿨다운이 지났고 최대 단계가 아닌지. 인증 자체를 막지는 않는다(ADR-038) */
    public boolean canGrowAt(LocalDateTime now, int cooldownDays) {
        if (stage >= gardenObject.getMaxStage()) {
            return false;
        }
        return lastGrownAt == null || !now.isBefore(lastGrownAt.plusDays(cooldownDays));
    }

    /** 정원 안에서 위치를 옮긴다. */
    public void moveTo(Short positionX, Short positionY) {
        this.positionX = positionX;
        this.positionY = positionY;
    }
}
