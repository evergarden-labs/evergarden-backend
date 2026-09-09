package com.evergarden.evergardenbackend.timecapsule.entity;

import com.evergarden.evergardenbackend.global.entity.BaseTimeEntity;
import com.evergarden.evergardenbackend.user.entity.User;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 봉인한 기록(TC-01).
 *
 * <p>해제 조건은 날짜와 위치 중 하나만 채운다. DB에도 CHECK로 강제되어 있어
 * 둘 다 채우거나 둘 다 비우면 저장되지 않는다.
 */
@Entity
@Getter
@Table(name = "time_capsules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimeCapsule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 60)
    private String title;

    /** {@code OPENED} 전에는 API로 내보내지 않는다 */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "unlock_type", nullable = false, length = 10)
    private UnlockType unlockType;

    @Column(name = "unlock_date")
    private LocalDate unlockDate;

    @Column(name = "unlock_lat", precision = 10, scale = 7)
    private BigDecimal unlockLat;

    @Column(name = "unlock_lng", precision = 10, scale = 7)
    private BigDecimal unlockLng;

    @Column(name = "unlock_radius_m")
    private Integer unlockRadiusM;

    /** 사용자가 알아볼 수 있게 저장해 둔 장소 이름 */
    @Column(name = "place_name", length = 60)
    private String placeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TimeCapsuleStatus status;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    private TimeCapsule(User owner, String title, String content, UnlockType unlockType) {
        this.owner = owner;
        this.title = title;
        this.content = content;
        this.unlockType = unlockType;
        this.status = TimeCapsuleStatus.SEALED;
    }

    /** 날짜가 되면 열리는 캡슐. 서버 배치가 상태를 바꾼다. */
    public static TimeCapsule sealUntilDate(User owner, String title, String content, LocalDate unlockDate) {
        TimeCapsule capsule = new TimeCapsule(owner, title, content, UnlockType.DATE);
        capsule.unlockDate = unlockDate;
        return capsule;
    }

    /** 그 장소에 가면 열리는 캡슐. 앱이 위치를 보고해야 판정된다(ADR-026). */
    public static TimeCapsule sealAtPlace(User owner, String title, String content,
                                          BigDecimal lat, BigDecimal lng, int radiusMeters, String placeName) {
        TimeCapsule capsule = new TimeCapsule(owner, title, content, UnlockType.LOCATION);
        capsule.unlockLat = lat;
        capsule.unlockLng = lng;
        capsule.unlockRadiusM = radiusMeters;
        capsule.placeName = placeName;
        return capsule;
    }

    /** 해제 조건이 충족됐다(TC-04). 아직 열어본 것은 아니다. */
    public void markUnlockable() {
        if (status == TimeCapsuleStatus.SEALED) {
            this.status = TimeCapsuleStatus.UNLOCKABLE;
        }
    }

    /** 열어본다(TC-05). 이때부터 내용이 조회된다. */
    public void open(LocalDateTime now) {
        this.status = TimeCapsuleStatus.OPENED;
        this.openedAt = now;
    }

    public boolean isOpened() {
        return status == TimeCapsuleStatus.OPENED;
    }

    public boolean isUnlockable() {
        return status == TimeCapsuleStatus.UNLOCKABLE;
    }

    public boolean isOwnedBy(Long userId) {
        return owner.getId().equals(userId);
    }
}
