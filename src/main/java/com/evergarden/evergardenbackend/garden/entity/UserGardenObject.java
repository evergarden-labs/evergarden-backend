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
import jakarta.persistence.Version;
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

    /**
     * 성장 경합(ADR-006과 같은 이유)을 막는 데 쓴다 — {@code UserGardenObjectRepository
     * .tryGrow()}가 조건부 {@code UPDATE ... WHERE version = ?}로 이 값을 확인한다.
     * JPA {@code @Version}으로 선언해 두면 엔티티를 직접 저장하는 다른 경로가 생겨도
     * 낙관적 락이 자동으로 걸린다.
     */
    @Version
    private Long version;

    @Builder
    private UserGardenObject(User user, GardenObject gardenObject, LocalDateTime unlockedAt) {
        this.user = user;
        this.gardenObject = gardenObject;
        this.unlockedAt = unlockedAt;
        this.stage = 1;
    }

    /**
     * 쿨다운이 지났고 최대 단계가 아닌지(GARDEN-02). 인증 자체를 막지는 않는다(ADR-038).
     *
     * <p>실제 성장은 여기서 엔티티를 바로 고치지 않고 {@code UserGardenObjectRepository
     * .tryGrow()}(조건부 UPDATE)로 한다 — 같은 사용자가 같은 오브젝트를 거의 동시에
     * 두 번 인증하면 두 트랜잭션이 이 판정을 똑같이 통과할 수 있어, 실제 반영은 DB가
     * {@code version} 일치 여부로 한 번만 받아주게 맡긴다.
     */
    public boolean canGrowAt(LocalDateTime now, int cooldownDays) {
        if (stage >= gardenObject.getMaxStage()) {
            return false;
        }
        return !now.isBefore(nextGrowableAt(cooldownDays));
    }

    /**
     * 다음 성장 가능 시각. 아직 한 번도 안 자랐어도({@code lastGrownAt == null}) 방금
     * 해금한 거라면 {@code unlockedAt}부터 쿨다운을 잰다 — 해금 직후 바로 재인증해도
     * 곧장 자라면 "7일 안 재인증은 변화 없음"(ADR-018)이 깨진다. {@code GET
     * /region-visits/{visitId}/reward}(GARDEN-02)가 {@code nextAvailableAt}을
     * 채울 때도 이 기준을 그대로 써야 해서 공개 메서드로 둔다.
     */
    public LocalDateTime nextGrowableAt(int cooldownDays) {
        LocalDateTime lastEvent = lastGrownAt != null ? lastGrownAt : unlockedAt;
        return lastEvent.plusDays(cooldownDays);
    }

    /** 정원 안에서 위치를 옮긴다. */
    public void moveTo(Short positionX, Short positionY) {
        this.positionX = positionX;
        this.positionY = positionY;
    }
}
