package com.evergarden.evergardenbackend.map.service;

import com.evergarden.evergardenbackend.garden.dto.GardenObjectResponse;
import com.evergarden.evergardenbackend.garden.repository.GardenObjectRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.map.dto.RegionDetail;
import com.evergarden.evergardenbackend.map.dto.RegionVisitStatus;
import com.evergarden.evergardenbackend.place.dto.PlaceSummary;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import com.evergarden.evergardenbackend.place.service.PlaceQueryService;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지역 상세·지역별 관광정보(MAP-03). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionService {

    private final RegionRepository regionRepository;
    private final GardenObjectRepository gardenObjectRepository;
    private final RegionVisitService regionVisitService;
    private final PlaceQueryService placeQueryService;

    /**
     * 지도에서 지역을 눌렀을 때 보여줄 정보(MAP-03). {@code availableGardenObjects}는
     * 그 지역 전용 오브젝트만 담는다 — 공통(지역 없음) 오브젝트는 이 지역을 다녀와서
     * 얻는 게 아니라서 "이 지역에서 얻을 수 있는" 목록에 넣지 않는다.
     */
    public RegionDetail getRegion(Long userId, String regionCode) {
        Region region = findRegion(regionCode);
        RegionVisitStatus status = regionVisitService.getStatus(userId, region);
        List<GardenObjectResponse> objects = gardenObjectRepository.findByRegion_Code(regionCode).stream()
                .map(GardenObjectResponse::of).toList();
        return new RegionDetail(
                region.getCode(), region.getName(), region.getLevel(),
                region.getCenterLat().doubleValue(), region.getCenterLng().doubleValue(),
                status.visited(), status.visitCount(), objects);
    }

    /**
     * 지역에 속한 관광지·음식점 훑어보기(MAP-03). {@code PlaceQueryService.search()}를
     * 그대로 위임한다 — 이미 동기화해 둔 {@code places} 테이블만 보는 점(라이브 TourAPI
     * 재호출 없음)까지 포함해 {@code /places/search}(PLAN-06)와 결과가 같다.
     *
     * <p>명세엔 이 오퍼레이션의 {@code 503 TOUR_API_UNAVAILABLE}이 문서화돼 있지만,
     * {@code search()}가 라이브 호출을 안 해서 지금 구조로는 실제로 던져질 일이 없다
     * ({@code searchPlaces}(PLAN-06)엔 애초에 이 코드가 문서화조차 안 돼 있어 같은 결론).
     * 나중에 이 경로가 라이브 조회로 바뀌면 그때 다시 볼 것.
     */
    public Page<PlaceSummary> listRegionPlaces(String regionCode, String contentTypeId, Pageable pageable) {
        return placeQueryService.search(null, regionCode, contentTypeId, pageable);
    }

    private Region findRegion(String regionCode) {
        return regionRepository.findById(regionCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.REGION_NOT_FOUND));
    }
}
