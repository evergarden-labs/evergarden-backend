package com.evergarden.evergardenbackend.media.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 업로드한 사진·영상. 아카이브와 타임캡슐이 함께 쓰는 공용 엔티티다.
 *
 * <p>파일 자체는 S3에 있고 여기에는 참조와 메타데이터만 둔다(ADR-023).
 * 사진의 촬영 시각·좌표·해상도는 서버가 EXIF에서 읽지만,
 * 영상은 서버가 열어보지 않으므로 앱이 보낸 값을 그대로 쓴다(ADR-052).
 */
@Entity
@Getter
@Table(name = "media")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Media extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User uploader;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MediaStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MediaType type;

    /** S3 오브젝트 키 */
    @Column(name = "storage_key", nullable = false, columnDefinition = "text")
    private String storageKey;

    /** 업로드가 끝나기 전에는 비어 있다 */
    @Column(columnDefinition = "text")
    private String url;

    /** 사진 리사이즈본. 영상은 항상 {@code null}이다(ADR-052) */
    @Column(name = "thumbnail_url", columnDefinition = "text")
    private String thumbnailUrl;

    private Integer width;

    private Integer height;

    /** 영상만 채워진다. 앱이 보낸 값이라 서버가 검증하지 못한다(ADR-052) */
    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** EXIF 촬영 시각. 아카이브 여행 기간의 재료다(ADR-030) */
    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    @Builder
    private Media(User uploader, MediaType type, String storageKey, Long sizeBytes,
                  Integer width, Integer height, Integer durationMs) {
        this.uploader = uploader;
        this.type = type;
        this.storageKey = storageKey;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
        this.durationMs = durationMs;
        this.status = MediaStatus.PENDING;
    }

    /**
     * 업로드 완료를 반영한다.
     *
     * <p>사진이면 서버가 EXIF에서 읽은 값을 넘기고, 영상이면 촬영 정보 없이
     * URL만 채운다. 영상의 해상도·길이는 생성 시점에 앱이 이미 보냈다(ADR-052).
     */
    public void complete(String url, String thumbnailUrl, Integer width, Integer height,
                         LocalDateTime takenAt, BigDecimal lat, BigDecimal lng) {
        this.url = url;
        this.thumbnailUrl = thumbnailUrl;
        if (width != null) {
            this.width = width;
        }
        if (height != null) {
            this.height = height;
        }
        this.takenAt = takenAt;
        this.lat = lat;
        this.lng = lng;
        this.status = MediaStatus.READY;
    }

    /** 아카이브·타임캡슐에 담을 수 있는 상태인지 */
    public boolean isReady() {
        return status == MediaStatus.READY;
    }
}
