package com.evergarden.evergardenbackend.place.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.dto.PlaceDetail;
import com.evergarden.evergardenbackend.place.dto.PlaceSummary;
import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.PlaceRepository;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관광지 검색·상세 조회(PLAN-06·07). 콘텐츠랩에서 미리 받아 {@code places} 테이블에
 * 적재해 둔 데이터만 조회한다 — 라이브로 TourAPI를 다시 부르지 않는다. 전국 8개 콘텐츠
 * 타입을 이미 다 동기화해 둬서, 조회 시점에 없는 데이터를 실시간으로 채울 필요가 없다.
 *
 * <p>{@code getPlace}의 {@code overview}·{@code useTime}·{@code restDate}·{@code imageUrls}는
 * 콘텐츠랩 상세조회 API가 있어야 채울 수 있는데, 이번 범위에는 없어 항상 비어 있다 —
 * docs/decisions.md 참고.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceQueryService {

    private final PlaceRepository placeRepository;
    private final RegionRepository regionRepository;

    public Page<PlaceSummary> search(String keyword, String regionCode, String contentTypeId, Pageable pageable) {
        if (keyword == null && regionCode == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        boolean filterByRegion = regionCode != null;
        List<String> regionCodes = filterByRegion ? resolveRegionCodes(regionCode) : List.of();

        return placeRepository.search(keyword, filterByRegion, regionCodes, contentTypeId, pageable)
                .map(PlaceSummary::of);
    }

    public PlaceDetail getPlace(Long placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLACE_NOT_FOUND));
        return PlaceDetail.of(place);
    }

    /**
     * 시/도면 그 자체 코드에 더해 하위 시군구 코드까지 포함한다 — 시/도로 직접 태그된
     * 장소도 있어서(위치기반 관광정보 중 일부는 시군구 코드 없이 온다) 시/도 코드 자체도 넣는다.
     */
    private List<String> resolveRegionCodes(String regionCode) {
        Region region = regionRepository.findById(regionCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.REGION_NOT_FOUND));
        if (region.getLevel() != RegionLevel.SIDO) {
            return List.of(region.getCode());
        }
        List<String> codes = new ArrayList<>(regionRepository.findByParent_Code(region.getCode()).stream()
                .map(Region::getCode)
                .toList());
        codes.add(region.getCode());
        return codes;
    }
}
