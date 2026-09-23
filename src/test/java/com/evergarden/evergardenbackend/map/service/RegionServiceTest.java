package com.evergarden.evergardenbackend.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.garden.entity.GardenObject;
import com.evergarden.evergardenbackend.garden.entity.GardenObjectType;
import com.evergarden.evergardenbackend.garden.repository.GardenObjectRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.map.dto.RegionDetail;
import com.evergarden.evergardenbackend.map.dto.RegionVisitStatus;
import com.evergarden.evergardenbackend.place.dto.PlaceSummary;
import com.evergarden.evergardenbackend.place.dto.RegionSummary;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import com.evergarden.evergardenbackend.place.service.PlaceQueryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/** 지역 상세·지역별 관광정보(MAP-03)를 확인한다. */
class RegionServiceTest {

    private static final Long USER_ID = 1L;

    private final RegionRepository regionRepository = mock(RegionRepository.class);
    private final GardenObjectRepository gardenObjectRepository = mock(GardenObjectRepository.class);
    private final RegionVisitService regionVisitService = mock(RegionVisitService.class);
    private final PlaceQueryService placeQueryService = mock(PlaceQueryService.class);
    private final RegionService service =
            new RegionService(regionRepository, gardenObjectRepository, regionVisitService, placeQueryService);

    private Region region(String code) {
        return Region.builder().code(code).level(RegionLevel.SIDO).name("서울특별시")
                .centerLat(new BigDecimal("37.5665")).centerLng(new BigDecimal("126.9780"))
                .syncedAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("없는 지역이면 REGION_NOT_FOUND")
    void 없는_지역() {
        given(regionRepository.findById("99")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRegion(USER_ID, "99"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGION_NOT_FOUND);
    }

    @Test
    @DisplayName("방문 상태는 RegionVisitService에 위임하고, 그 지역 전용 오브젝트만 담는다")
    void 정상_조회() {
        Region seoul = region("11");
        given(regionRepository.findById("11")).willReturn(Optional.of(seoul));
        given(regionVisitService.getStatus(USER_ID, seoul))
                .willReturn(new RegionVisitStatus(RegionSummary.of(seoul), true, 3, LocalDateTime.now()));

        GardenObject object = GardenObject.builder()
                .region(seoul).name("서울 나무").type(GardenObjectType.PLANT).maxStage((short) 3).build();
        ReflectionTestUtils.setField(object, "id", 10L);
        given(gardenObjectRepository.findByRegion_Code("11")).willReturn(List.of(object));

        RegionDetail result = service.getRegion(USER_ID, "11");

        assertThat(result.code()).isEqualTo("11");
        assertThat(result.visited()).isTrue();
        assertThat(result.visitCount()).isEqualTo(3);
        assertThat(result.availableGardenObjects()).hasSize(1);
        assertThat(result.availableGardenObjects().get(0).gardenObjectId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("그 지역에 지정된 오브젝트가 없으면 빈 목록")
    void 오브젝트_없음() {
        Region seoul = region("11");
        given(regionRepository.findById("11")).willReturn(Optional.of(seoul));
        given(regionVisitService.getStatus(USER_ID, seoul))
                .willReturn(new RegionVisitStatus(RegionSummary.of(seoul), false, 0, null));
        given(gardenObjectRepository.findByRegion_Code("11")).willReturn(List.of());

        RegionDetail result = service.getRegion(USER_ID, "11");

        assertThat(result.availableGardenObjects()).isEmpty();
    }

    @Test
    @DisplayName("지역별 관광정보는 PlaceQueryService.search를 keyword=null로 위임한다")
    void 지역별_관광정보_위임() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<PlaceSummary> page = new PageImpl<>(List.of());
        given(placeQueryService.search(isNull(), eq("11"), eq("12"), any())).willReturn(page);

        Page<PlaceSummary> result = service.listRegionPlaces("11", "12", pageable);

        assertThat(result).isSameAs(page);
        verify(placeQueryService).search(isNull(), eq("11"), eq("12"), eq(pageable));
    }
}
