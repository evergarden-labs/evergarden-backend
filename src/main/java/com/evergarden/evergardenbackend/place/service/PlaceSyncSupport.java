package com.evergarden.evergardenbackend.place.service;

import com.evergarden.evergardenbackend.place.entity.Region;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 초기 시딩({@link PlaceSyncService})과 일일 증분 동기화({@link PlaceIncrementalSyncService})가
 * 똑같이 쓰는 지역 매칭·좌표 파싱 로직. 둘 다 TourAPI의 {@code areaBasedList2} 계열
 * 응답을 다루므로 한곳에 모아 둔다.
 */
final class PlaceSyncSupport {

    private PlaceSyncSupport() {
    }

    /** 시군구 코드가 있고 우리 {@code Region}에 있으면 그 시군구로, 아니면 시/도 코드로 찾는다. */
    static Region resolveRegion(String regnCd, String signguCd, Map<String, Region> regionsByCode) {
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

    static BigDecimal parseCoordinate(String value) {
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
