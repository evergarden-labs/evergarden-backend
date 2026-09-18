package com.evergarden.evergardenbackend.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.place.client.TourApiClient;
import com.evergarden.evergardenbackend.place.client.dto.AreaBasedItem;
import com.evergarden.evergardenbackend.place.client.dto.AreaBasedPage;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** 전국 관광지 초기 시딩 — 지역 매칭·좌표 검증·페이지네이션·항목별 장애 격리를 확인한다. */
class PlaceSyncServiceTest {

    private final TourApiClient tourApiClient = mock(TourApiClient.class);
    private final PlaceUpsertService placeUpsertService = mock(PlaceUpsertService.class);
    private final RegionRepository regionRepository = mock(RegionRepository.class);
    private final PlaceSyncService service =
            new PlaceSyncService(tourApiClient, placeUpsertService, regionRepository);

    private Region sido;
    private Region sigungu;

    private Region region(String code, RegionLevel level) {
        return Region.builder().code(code).level(level).name("테스트지역")
                .centerLat(BigDecimal.ZERO).centerLng(BigDecimal.ZERO)
                .syncedAt(LocalDateTime.now()).build();
    }

    private AreaBasedItem item(String contentId, String regnCd, String signguCd, String mapx, String mapy) {
        return new AreaBasedItem(contentId, "12", "테스트 장소", "주소", "02-000-0000", mapx, mapy,
                "http://example.com/thumb.jpg", regnCd, signguCd);
    }

    @BeforeEach
    void setUp() {
        sido = region("11", RegionLevel.SIDO);
        sigungu = region("11110", RegionLevel.SIGUNGU);
        given(regionRepository.findAll()).willReturn(List.of(sido, sigungu));
        given(tourApiClient.fetchPlaces(any(), any(), anyInt(), anyInt()))
                .willReturn(new AreaBasedPage(List.of(), 0));
    }

    @Test
    @DisplayName("시군구 코드가 맞으면 그 시군구 Region으로 upsert를 위임한다")
    void 정상_위임() {
        given(tourApiClient.fetchPlaces(eq("11"), eq("12"), eq(1), anyInt()))
                .willReturn(new AreaBasedPage(List.of(item("c1", "11", "110", "126.97", "37.57")), 1));
        given(placeUpsertService.upsertOne(any(), any(), any(), any(), any())).willReturn(true);

        PlaceSyncResult result = service.syncAll();

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skippedContentIds()).isEmpty();
        verify(placeUpsertService).upsertOne(any(), eq(sigungu), any(), any(), any());
    }

    @Test
    @DisplayName("좌표가 없으면 upsert를 부르지 않고 건너뛴 목록에 담는다")
    void 좌표없음_건너뜀() {
        given(tourApiClient.fetchPlaces(eq("11"), eq("12"), eq(1), anyInt()))
                .willReturn(new AreaBasedPage(List.of(item("c1", "11", "110", "", "")), 1));

        PlaceSyncResult result = service.syncAll();

        assertThat(result.created()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.skippedContentIds()).containsExactly("c1");
        verify(placeUpsertService, org.mockito.Mockito.never()).upsertOne(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("시군구 코드가 우리 Region에 없으면 시/도 코드로 대신 찾는다")
    void 시군구없으면_시도로_대체() {
        given(tourApiClient.fetchPlaces(eq("11"), eq("12"), eq(1), anyInt()))
                .willReturn(new AreaBasedPage(List.of(item("c1", "11", "999", "126.97", "37.57")), 1));
        given(placeUpsertService.upsertOne(any(), any(), any(), any(), any())).willReturn(true);

        PlaceSyncResult result = service.syncAll();

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skippedContentIds()).isEmpty();
        verify(placeUpsertService).upsertOne(any(), eq(sido), any(), any(), any());
    }

    @Test
    @DisplayName("시/도 코드조차 없으면 건너뛴다")
    void 지역코드_전혀없음() {
        given(tourApiClient.fetchPlaces(eq("11"), eq("12"), eq(1), anyInt()))
                .willReturn(new AreaBasedPage(List.of(item("c1", "", "", "126.97", "37.57")), 1));

        PlaceSyncResult result = service.syncAll();

        assertThat(result.skippedContentIds()).containsExactly("c1");
    }

    @Test
    @DisplayName("totalCount가 페이지 크기보다 크면 다음 페이지까지 호출한다")
    void 페이지네이션() {
        given(tourApiClient.fetchPlaces(eq("11"), eq("12"), eq(1), eq(100)))
                .willReturn(new AreaBasedPage(List.of(item("c1", "11", "110", "126.97", "37.57")), 150));
        given(tourApiClient.fetchPlaces(eq("11"), eq("12"), eq(2), eq(100)))
                .willReturn(new AreaBasedPage(List.of(item("c2", "11", "110", "126.98", "37.58")), 150));
        given(placeUpsertService.upsertOne(any(), any(), any(), any(), any())).willReturn(true);

        PlaceSyncResult result = service.syncAll();

        assertThat(result.created()).isEqualTo(2);
        verify(tourApiClient).fetchPlaces(eq("11"), eq("12"), eq(1), eq(100));
        verify(tourApiClient).fetchPlaces(eq("11"), eq("12"), eq(2), eq(100));
    }

    @Test
    @DisplayName("한 항목 저장이 실패해도(예: 컬럼 길이 초과) 건너뛰고 계속 진행한다 — 실전에서 확인된 문제")
    void 항목저장_실패해도_계속진행() {
        given(tourApiClient.fetchPlaces(eq("11"), eq("12"), eq(1), anyInt()))
                .willReturn(new AreaBasedPage(List.of(
                        item("c1", "11", "110", "126.97", "37.57"),
                        item("c2", "11", "110", "126.98", "37.58")), 2));
        given(placeUpsertService.upsertOne(any(), any(), any(), any(), any()))
                .willThrow(new DataIntegrityViolationException("value too long"))
                .willReturn(true);

        PlaceSyncResult result = service.syncAll();

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skippedContentIds()).containsExactly("c1");
    }
}
