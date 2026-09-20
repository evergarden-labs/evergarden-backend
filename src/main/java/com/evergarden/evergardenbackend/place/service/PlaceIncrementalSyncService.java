package com.evergarden.evergardenbackend.place.service;

import com.evergarden.evergardenbackend.place.client.TourApiClient;
import com.evergarden.evergardenbackend.place.client.dto.AreaBasedSyncPage;
import com.evergarden.evergardenbackend.place.client.dto.SyncAreaBasedItem;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 전국 관광지 일일 증분 동기화({@code docs/place-data-sync.md} 2.3절). 매일
 * {@code areaBasedSyncList2}로 어제 이후 바뀐 것만 받아 반영한다 — 초기 시딩
 * ({@link PlaceSyncService})과 달리 지역·타입으로 나누지 않고 전국을 한 번에 받는다.
 *
 * <p>{@code showflag=0}(콘텐츠가 내려감)은 이번 범위에서 삭제·숨김 처리를 하지 않고
 * 건너뛴다 — {@code places}에 그 상태를 표현할 컬럼이 없고, 이미 일정에 담긴 장소를
 * 무턱대고 지우면 {@code trip_places}의 외래키 제약과 부딪힌다(docs/decisions.md 참고).
 *
 * <p>초기 시딩과 같은 이유로 이 클래스는 {@code @Transactional}이 아니다 — 실제 저장은
 * {@link PlaceUpsertService}의 항목별 독립 트랜잭션으로 한다.
 */
@Service
@RequiredArgsConstructor
public class PlaceIncrementalSyncService {

    private static final DateTimeFormatter MODIFIED_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int PAGE_SIZE = 100;

    private final TourApiClient tourApiClient;
    private final PlaceUpsertService placeUpsertService;
    private final RegionRepository regionRepository;

    public PlaceIncrementalSyncResult syncSince(LocalDate modifiedSince) {
        Map<String, Region> regionsByCode = regionRepository.findAll().stream()
                .collect(Collectors.toMap(Region::getCode, r -> r));
        String modifiedTime = modifiedSince.format(MODIFIED_TIME_FORMAT);
        LocalDateTime now = LocalDateTime.now();

        int created = 0;
        int updated = 0;
        int ignoredDelisted = 0;
        List<String> skipped = new ArrayList<>();

        int page = 1;
        while (true) {
            AreaBasedSyncPage result = tourApiClient.fetchSyncedPlaces(modifiedTime, page, PAGE_SIZE);
            for (SyncAreaBasedItem item : result.items()) {
                if (!item.isVisible()) {
                    ignoredDelisted++;
                    continue;
                }
                Region region = PlaceSyncSupport.resolveRegion(item.lDongRegnCd(), item.lDongSignguCd(), regionsByCode);
                BigDecimal lat = PlaceSyncSupport.parseCoordinate(item.mapy());
                BigDecimal lng = PlaceSyncSupport.parseCoordinate(item.mapx());
                if (region == null || lat == null || lng == null) {
                    skipped.add(item.contentid());
                    continue;
                }
                try {
                    boolean isNew = placeUpsertService.upsertOne(item.toAreaBasedItem(), region, lat, lng, now);
                    if (isNew) {
                        created++;
                    } else {
                        updated++;
                    }
                } catch (RuntimeException e) {
                    skipped.add(item.contentid());
                }
            }
            if ((long) page * PAGE_SIZE >= result.totalCount()) {
                break;
            }
            page++;
        }

        return new PlaceIncrementalSyncResult(created, updated, ignoredDelisted, skipped);
    }
}
