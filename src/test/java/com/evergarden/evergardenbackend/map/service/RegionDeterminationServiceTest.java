package com.evergarden.evergardenbackend.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.place.client.TourApiClient;
import com.evergarden.evergardenbackend.place.client.dto.LocationBasedItem;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

/** 좌표 → 지역 판정(MAP-01)이 "가장 가까운 항목의 지역코드"를 쓰는지 확인한다. */
class RegionDeterminationServiceTest {

    private static final BigDecimal LAT = new BigDecimal("37.5729");
    private static final BigDecimal LNG = new BigDecimal("126.9794");

    private final TourApiClient tourApiClient = mock(TourApiClient.class);
    private final RegionRepository regionRepository = mock(RegionRepository.class);
    private final RegionDeterminationService service =
            new RegionDeterminationService(tourApiClient, regionRepository);

    private Region region(String code) {
        return Region.builder().code(code).level(RegionLevel.SIGUNGU).name(code)
                .centerLat(BigDecimal.ONE).centerLng(BigDecimal.ONE).syncedAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("가장 가까운 항목(첫 번째)의 시군구 코드로 판정한다")
    void 최근접_항목_시군구코드() {
        LocationBasedItem near = new LocationBasedItem("c1", "12", "가까운 곳", "11", "110");
        LocationBasedItem far = new LocationBasedItem("c2", "12", "먼 곳", "11", "140");
        given(tourApiClient.fetchNearby(LAT, LNG, 2_000)).willReturn(List.of(near, far));
        Region jongno = region("110");
        given(regionRepository.findById("110")).willReturn(Optional.of(jongno));

        Region result = service.determine(LAT, LNG);

        assertThat(result).isSameAs(jongno);
    }

    @Test
    @DisplayName("시군구 코드가 없으면 시/도 코드로 대체 판정한다")
    void 시군구코드_없으면_시도코드() {
        LocationBasedItem near = new LocationBasedItem("c1", "12", "가까운 곳", "11", "");
        given(tourApiClient.fetchNearby(LAT, LNG, 2_000)).willReturn(List.of(near));
        Region seoul = region("11");
        given(regionRepository.findById("11")).willReturn(Optional.of(seoul));

        Region result = service.determine(LAT, LNG);

        assertThat(result).isSameAs(seoul);
    }

    @Test
    @DisplayName("반경 안에 아무것도 없으면 REGION_NOT_DETERMINED")
    void 결과없음() {
        given(tourApiClient.fetchNearby(LAT, LNG, 2_000)).willReturn(List.of());

        assertThatThrownBy(() -> service.determine(LAT, LNG))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGION_NOT_DETERMINED);
    }

    @Test
    @DisplayName("시군구·시도 코드가 둘 다 없으면 REGION_NOT_DETERMINED")
    void 지역코드_둘다없음() {
        LocationBasedItem near = new LocationBasedItem("c1", "12", "이상한 곳", "", "");
        given(tourApiClient.fetchNearby(LAT, LNG, 2_000)).willReturn(List.of(near));

        assertThatThrownBy(() -> service.determine(LAT, LNG))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGION_NOT_DETERMINED);
    }

    @Test
    @DisplayName("지역코드는 받았는데 우리 regions에 없으면 REGION_NOT_DETERMINED")
    void 우리지역에없음() {
        LocationBasedItem near = new LocationBasedItem("c1", "12", "가까운 곳", "11", "999");
        given(tourApiClient.fetchNearby(LAT, LNG, 2_000)).willReturn(List.of(near));
        given(regionRepository.findById("999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.determine(LAT, LNG))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGION_NOT_DETERMINED);
    }

    @Test
    @DisplayName("콘텐츠랩 호출 자체가 실패하면 TOUR_API_UNAVAILABLE")
    void 호출실패() {
        given(tourApiClient.fetchNearby(LAT, LNG, 2_000))
                .willThrow(new RestClientException("연결 실패"));

        assertThatThrownBy(() -> service.determine(LAT, LNG))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOUR_API_UNAVAILABLE);
    }
}
