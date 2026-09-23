package com.evergarden.evergardenbackend.map.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.client.TourApiClient;
import com.evergarden.evergardenbackend.place.client.dto.LocationBasedItem;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * 좌표로 현재 지역을 판정한다(MAP-01). 콘텐츠랩에는 좌표 하나로 바로 지역을 물어보는
 * API가 없어, 근처 관광지를 거리순으로 받아 **가장 가까운 항목의 지역코드**를 쓴다
 * (ADR-004). 반경을 넉넉히 2km로 잡은 이유는 관광지 밀도가 낮은 지역에서 반경을
 * 너무 좁게 잡으면 {@code REGION_NOT_DETERMINED}가 남발되기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class RegionDeterminationService {

    private static final int SEARCH_RADIUS_METERS = 2_000;

    private final TourApiClient tourApiClient;
    private final RegionRepository regionRepository;

    /**
     * @throws BusinessException {@code TOUR_API_UNAVAILABLE} — 콘텐츠랩 호출 자체가 실패
     * @throws BusinessException {@code REGION_NOT_DETERMINED} — 반경 안에 관광지가 없거나,
     *                            있어도 지역코드가 없거나, 그 코드가 우리 {@code regions}에 없음
     */
    public Region determine(BigDecimal lat, BigDecimal lng) {
        List<LocationBasedItem> nearby;
        try {
            nearby = tourApiClient.fetchNearby(lat, lng, SEARCH_RADIUS_METERS);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.TOUR_API_UNAVAILABLE);
        }
        if (nearby.isEmpty()) {
            throw new BusinessException(ErrorCode.REGION_NOT_DETERMINED);
        }

        String regionCode = resolveRegionCode(nearby.get(0));
        if (regionCode == null) {
            throw new BusinessException(ErrorCode.REGION_NOT_DETERMINED);
        }
        return regionRepository.findById(regionCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.REGION_NOT_DETERMINED));
    }

    /** 시군구 코드가 없는 항목도 있다 — 그럴 땐 시/도까지만이라도 판정한다. */
    private String resolveRegionCode(LocationBasedItem nearest) {
        if (nearest.lDongSignguCd() != null && !nearest.lDongSignguCd().isBlank()) {
            return nearest.lDongSignguCd();
        }
        if (nearest.lDongRegnCd() != null && !nearest.lDongRegnCd().isBlank()) {
            return nearest.lDongRegnCd();
        }
        return null;
    }
}
