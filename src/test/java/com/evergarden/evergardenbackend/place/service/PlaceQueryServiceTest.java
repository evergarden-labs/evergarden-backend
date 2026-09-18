package com.evergarden.evergardenbackend.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.dto.PlaceDetail;
import com.evergarden.evergardenbackend.place.entity.Place;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.PlaceRepository;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/** 관광지 검색·상세 조회(PLAN-06·07)의 검증 순서와 지역 확장 로직을 확인한다. */
class PlaceQueryServiceTest {

    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final RegionRepository regionRepository = mock(RegionRepository.class);
    private final PlaceDetailFetchService placeDetailFetchService = mock(PlaceDetailFetchService.class);
    private final PlaceQueryService service =
            new PlaceQueryService(placeRepository, regionRepository, placeDetailFetchService);

    private final Pageable pageable = PageRequest.of(0, 20);

    private Region region(String code, RegionLevel level, Region parent) {
        Region region = Region.builder().code(code).level(level).name("지역" + code)
                .centerLat(BigDecimal.ZERO).centerLng(BigDecimal.ZERO).syncedAt(LocalDateTime.now()).build();
        if (parent != null) {
            ReflectionTestUtils.setField(region, "parent", parent);
        }
        return region;
    }

    private Place place(Long id, Region region) {
        Place place = Place.builder().contentId("c" + id).contentTypeId("12").title("장소" + id)
                .lat(BigDecimal.ONE).lng(BigDecimal.ONE).region(region).build();
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }

    // ── 검색 ─────────────────────────────────────────────

    @Test
    @DisplayName("keyword와 regionCode가 둘 다 없으면 INVALID_REQUEST")
    void 검색_조건없음() {
        assertThatThrownBy(() -> service.search(null, null, null, pageable))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
        verify(placeRepository, never()).search(any(), anyBoolean(), any(), any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 regionCode는 REGION_NOT_FOUND")
    void 검색_없는지역() {
        given(regionRepository.findById("999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.search(null, "999", null, pageable))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGION_NOT_FOUND);
    }

    @Test
    @DisplayName("keyword만 있으면 지역 필터 없이 검색한다")
    void 검색_키워드만() {
        given(placeRepository.search(eq("남산"), eq(false), any(), eq(null), any()))
                .willReturn(new PageImpl<>(List.of(place(1L, region("11", RegionLevel.SIDO, null)))));

        var result = service.search("남산", null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(regionRepository, never()).findById(any());
    }

    @Test
    @DisplayName("시/도 코드로 검색하면 하위 시군구까지 포함해서 필터한다")
    void 검색_시도확장() {
        Region sido = region("11", RegionLevel.SIDO, null);
        Region gu1 = region("11010", RegionLevel.SIGUNGU, sido);
        Region gu2 = region("11020", RegionLevel.SIGUNGU, sido);
        given(regionRepository.findById("11")).willReturn(Optional.of(sido));
        given(regionRepository.findByParent_Code("11")).willReturn(List.of(gu1, gu2));
        given(placeRepository.search(any(), eq(true), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));

        service.search(null, "11", null, pageable);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(placeRepository).search(eq(null), eq(true), captor.capture(), eq(null), any());
        assertThat(captor.getValue()).containsExactlyInAnyOrder("11", "11010", "11020");
    }

    @Test
    @DisplayName("시군구 코드로 검색하면 그 코드 하나만으로 필터한다")
    void 검색_시군구단독() {
        Region gu = region("11010", RegionLevel.SIGUNGU, region("11", RegionLevel.SIDO, null));
        given(regionRepository.findById("11010")).willReturn(Optional.of(gu));
        given(placeRepository.search(any(), eq(true), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));

        service.search(null, "11010", null, pageable);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(placeRepository).search(eq(null), eq(true), captor.capture(), eq(null), any());
        assertThat(captor.getValue()).containsExactly("11010");
        verify(regionRepository, never()).findByParent_Code(any());
    }

    // ── 상세 조회 ─────────────────────────────────────────

    @Test
    @DisplayName("없는 장소는 PLACE_NOT_FOUND")
    void 상세_없는장소() {
        given(placeRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPlace(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PLACE_NOT_FOUND);
    }

    @Test
    @DisplayName("상세 조회는 상세 정보 채우기를 위임하고, 채워진 값을 그대로 돌려준다")
    void 상세_정상() {
        Place place = place(1L, region("11", RegionLevel.SIDO, null));
        given(placeRepository.findById(1L)).willReturn(Optional.of(place));

        PlaceDetail detail = service.getPlace(1L);

        verify(placeDetailFetchService).ensureDetailFetched(place);
        assertThat(detail.placeId()).isEqualTo(1L);
        // ensureDetailFetched를 모킹해서 실제로 안 채워지므로 비어 있는 게 맞다
        assertThat(detail.overview()).isNull();
        assertThat(detail.imageUrls()).isEmpty();
    }
}
