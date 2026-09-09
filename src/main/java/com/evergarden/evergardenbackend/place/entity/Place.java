package com.evergarden.evergardenbackend.place.entity;

import com.evergarden.evergardenbackend.global.entity.BaseTimeEntity;
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

/**
 * 관광지. 콘텐츠랩에서 받아 적재한 캐시다(ADR-004).
 *
 * <p>매 요청마다 외부 API를 부르지 않는 이유는 세 가지다.
 * 다른 테이블이 여기에 FK를 걸어야 하고, 코드가 거의 바뀌지 않으며,
 * 외부 장애가 나면 일정 편집이 통째로 멈추기 때문이다.
 */
@Entity
@Getter
@Table(name = "places")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 콘텐츠랩 contentid */
    @Column(name = "content_id", nullable = false, length = 20, unique = true)
    private String contentId;

    /** 관광지·음식점·숙박 등 분류 코드 */
    @Column(name = "content_type_id", length = 10)
    private String contentTypeId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 300)
    private String addr;

    @Column(length = 50)
    private String tel;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal lng;

    /** 게시물 지역 스냅샷을 뽑는 출발점(ADR-003) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_code", nullable = false)
    private Region region;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(columnDefinition = "text")
    private String overview;

    /** 이용 시간. 콘텐츠랩 원문 그대로 두고 파싱하지 않는다(ADR-049) */
    @Column(name = "use_time", columnDefinition = "text")
    private String useTime;

    /** 휴무일. 원문 그대로 */
    @Column(name = "rest_date", columnDefinition = "text")
    private String restDate;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @Builder
    private Place(String contentId, String contentTypeId, String title, String addr, String tel,
                  BigDecimal lat, BigDecimal lng, Region region, String thumbnailUrl,
                  String overview, String useTime, String restDate, LocalDateTime syncedAt) {
        this.contentId = contentId;
        this.contentTypeId = contentTypeId;
        this.title = title;
        this.addr = addr;
        this.tel = tel;
        this.lat = lat;
        this.lng = lng;
        this.region = region;
        this.thumbnailUrl = thumbnailUrl;
        this.overview = overview;
        this.useTime = useTime;
        this.restDate = restDate;
        this.syncedAt = syncedAt;
    }

    /** 콘텐츠랩에서 다시 받아 갱신한다. */
    public void sync(String title, String addr, String tel, BigDecimal lat, BigDecimal lng,
                     Region region, String thumbnailUrl, String overview,
                     String useTime, String restDate, LocalDateTime syncedAt) {
        this.title = title;
        this.addr = addr;
        this.tel = tel;
        this.lat = lat;
        this.lng = lng;
        this.region = region;
        this.thumbnailUrl = thumbnailUrl;
        this.overview = overview;
        this.useTime = useTime;
        this.restDate = restDate;
        this.syncedAt = syncedAt;
    }
}
