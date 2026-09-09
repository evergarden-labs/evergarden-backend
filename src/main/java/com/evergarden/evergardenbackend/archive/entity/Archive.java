package com.evergarden.evergardenbackend.archive.entity;

import com.evergarden.evergardenbackend.global.entity.BaseTimeEntity;
import com.evergarden.evergardenbackend.trip.entity.Trip;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 테마 앨범.
 *
 * <p>여행 일정과 1:1로 이어지지만 서로를 필요로 하지 않는다.
 * 연결은 이쪽이 들고 있고 {@code ON DELETE SET NULL}이라,
 * 일정을 지워도 아카이브는 남고 연결만 끊긴다(ADR-001).
 *
 * <p>여행 기간은 사용자가 입력하지 않는다. 담긴 사진의 촬영 시각에서 계산되는
 * 파생값이라 사진이 없으면 비어 있다(ADR-030).
 */
@Entity
@Getter
@Table(name = "archives")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Archive extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", unique = true)
    private Trip trip;

    @Column(nullable = false, length = 60)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ArchiveTheme theme;

    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    /** 대표 사진(ARCH-08). 영상은 지정할 수 없다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_item_id")
    private ArchiveItem coverItem;

    /** 파생값 — 담긴 사진 촬영일의 최솟값(ADR-030) */
    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "collaboration_status", nullable = false, length = 10)
    private CollaborationStatus collaborationStatus;

    /** 복제해서 만든 아카이브면 원본(ADR-005) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_archive_id")
    private Archive originArchive;

    @Builder
    private Archive(User owner, Trip trip, String title, ArchiveTheme theme,
                    String primaryColor, Archive originArchive) {
        this.owner = owner;
        this.trip = trip;
        this.title = title;
        this.theme = theme;
        this.primaryColor = primaryColor;
        this.originArchive = originArchive;
        this.collaborationStatus = CollaborationStatus.NONE;
    }

    /**
     * 이름·테마·대표 색상을 바꾼다(ARCH-02). {@code null}인 인자는 그대로 둔다.
     *
     * <p>테마를 바꿔도 배치를 다시 계산하지 않는다. 공들여 맞춘 구성이
     * 말없이 흐트러지면 안 되므로 앱이 다시 조정하게 한다.
     */
    public void update(String title, ArchiveTheme theme, String primaryColor) {
        if (title != null) {
            this.title = title;
        }
        if (theme != null) {
            this.theme = theme;
        }
        if (primaryColor != null) {
            this.primaryColor = primaryColor;
        }
    }

    /** 여행 일정을 잇거나({@code trip}) 끊는다({@code null}) — ARCH-17. */
    public void linkTrip(Trip trip) {
        this.trip = trip;
    }

    public void changeCover(ArchiveItem coverItem) {
        this.coverItem = coverItem;
    }

    /** 담긴 사진의 촬영일 범위로 여행 기간을 다시 계산한다(ADR-030). */
    public void refreshPeriod(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** 처음 초대할 때 공동 편집이 열린다(ARCH-10). */
    public void openCollaboration() {
        if (collaborationStatus == CollaborationStatus.NONE) {
            this.collaborationStatus = CollaborationStatus.OPEN;
        }
    }

    /** 공동 편집을 끝낸다(ARCH-14). 참여자는 이후 조회만 할 수 있다. */
    public void closeCollaboration() {
        this.collaborationStatus = CollaborationStatus.CLOSED;
    }

    /** 편집을 받을 수 있는 상태인지. 종료됐으면 쓰기만 막힌다(ADR-017). */
    public boolean isEditable() {
        return collaborationStatus != CollaborationStatus.CLOSED;
    }

    public boolean isOwnedBy(Long userId) {
        return owner.getId().equals(userId);
    }
}
