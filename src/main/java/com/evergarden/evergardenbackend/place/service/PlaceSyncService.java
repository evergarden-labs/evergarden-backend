package com.evergarden.evergardenbackend.place.service;

import com.evergarden.evergardenbackend.place.client.TourApiClient;
import com.evergarden.evergardenbackend.place.client.dto.AreaBasedItem;
import com.evergarden.evergardenbackend.place.client.dto.AreaBasedPage;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 전국 관광지 초기 시딩({@code docs/place-data-sync.md} 2.2절).
 *
 * <p>시/도 단위로 {@code areaBasedList2}를 불러 그 안의 모든 시군구 결과를 한 번에
 * 받는다 — 응답 항목마다 {@code lDongSignguCd}가 있어 시군구는 항목별로 구분한다.
 *
 * <p><b>이 클래스 자체는 {@code @Transactional}이 아니다.</b> 실제 저장은 항목마다
 * {@link PlaceUpsertService}의 독립된 트랜잭션으로 한다 — 전체를 트랜잭션 하나로 묶었다가
 * 실전에서 사고가 났다(이 클래스 주석 참고). 항목 하나가 실패해도(예: 예상 못 한 데이터
 * 형식) 로그만 남기고 건너뛴 뒤 계속 진행한다 — 외부 데이터라 완벽을 기대할 수 없다.
 *
 * <p>{@code contentId} 기준 upsert라 중간에 실패해도 다시 돌리면 이어받은 것처럼
 * 동작한다 — 이미 받은 항목은 갱신만 되고 중복 생성되지 않는다. 그래서 지역·타입별
 * "어디까지 받았다" 체크포인트를 따로 두지 않았다 — 지금 규모(전국 8개 타입,
 * 약 5만 건)는 한 번에 끝나서 그런 장치가 필요 없었다. 나중에 규모가 커지면 다시 봐야 한다.
 *
 * <p>{@code detailIntro2}(소개정보) 연동 전이라 {@code useTime}·{@code restDate}는
 * 아직 안 채운다 — 이미 있던 값이 있으면 갱신 때 지우지 않고 그대로 둔다.
 */
@Service
@RequiredArgsConstructor
public class PlaceSyncService {

    private static final List<String> CONTENT_TYPE_IDS =
            List.of("12", "14", "15", "25", "28", "32", "38", "39");
    private static final int PAGE_SIZE = 100;

    private final TourApiClient tourApiClient;
    private final PlaceUpsertService placeUpsertService;
    private final RegionRepository regionRepository;

    public PlaceSyncResult syncAll() {
        Map<String, Region> regionsByCode = regionRepository.findAll().stream()
                .collect(Collectors.toMap(Region::getCode, r -> r));
        List<Region> provinces = regionsByCode.values().stream()
                .filter(r -> r.getLevel() == RegionLevel.SIDO)
                .toList();

        LocalDateTime now = LocalDateTime.now();
        int created = 0;
        int updated = 0;
        List<String> skipped = new ArrayList<>();

        for (Region province : provinces) {
            for (String contentTypeId : CONTENT_TYPE_IDS) {
                int page = 1;
                while (true) {
                    AreaBasedPage result = tourApiClient.fetchPlaces(province.getCode(), contentTypeId, page, PAGE_SIZE);
                    for (AreaBasedItem item : result.items()) {
                        Region region = resolveRegion(item, regionsByCode);
                        BigDecimal lat = parseCoordinate(item.mapy());
                        BigDecimal lng = parseCoordinate(item.mapx());
                        if (region == null || lat == null || lng == null) {
                            skipped.add(item.contentid());
                            continue;
                        }
                        try {
                            boolean isNew = placeUpsertService.upsertOne(item, region, lat, lng, now);
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
            }
        }
        return new PlaceSyncResult(created, updated, skipped);
    }

    /** 시군구 코드가 있으면 시/도+시군구로, 없으면 시/도 코드로 찾는다(ADR: 지역 코드 전국유일화). */
    private Region resolveRegion(AreaBasedItem item, Map<String, Region> regionsByCode) {
        String signguCd = item.lDongSignguCd();
        String regnCd = item.lDongRegnCd();
        if (regnCd == null || regnCd.isBlank()) {
            return null;
        }
        if (signguCd != null && !signguCd.isBlank()) {
            Region region = regionsByCode.get(regnCd + signguCd);
            if (region != null) {
                return region;
            }
        }
        return regionsByCode.get(regnCd);
    }

    private BigDecimal parseCoordinate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
