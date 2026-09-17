package com.evergarden.evergardenbackend.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.place.client.TourApiClient;
import com.evergarden.evergardenbackend.place.client.dto.AreaBasedSyncPage;
import com.evergarden.evergardenbackend.place.client.dto.SyncAreaBasedItem;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** 일일 증분 동기화 — 전국 단일 호출·비표출 건너뜀·항목별 장애 격리를 확인한다. */
class PlaceIncrementalSyncServiceTest {

    private final TourApiClient tourApiClient = mock(TourApiClient.class);
    private final PlaceUpsertService placeUpsertService = mock(PlaceUpsertService.class);
    private final RegionRepository regionRepository = mock(RegionRepository.class);
    private final PlaceIncrementalSyncService service =
            new PlaceIncrementalSyncService(tourApiClient, placeUpsertService, regionRepository);

    private Region sido;
    private Region sigungu;
    private final LocalDate since = LocalDate.of(2026, 9, 17);

    private Region region(String code, RegionLevel level) {
        return Region.builder().code(code).level(level).name("테스트지역")
                .centerLat(BigDecimal.ZERO).centerLng(BigDecimal.ZERO)
                .syncedAt(LocalDateTime.now()).build();
    }

    private SyncAreaBasedItem item(String contentId, String regnCd, String signguCd,
                                    String mapx, String mapy, String showflag) {
        return new SyncAreaBasedItem(contentId, "12", "테스트 장소", "주소", "02-000-0000", mapx, mapy,
                "http://example.com/thumb.jpg", regnCd, signguCd, showflag);
    }

    @BeforeEach
    void setUp() {
        sido = region("11", RegionLevel.SIDO);
        sigungu = region("11110", RegionLevel.SIGUNGU);
        given(regionRepository.findAll()).willReturn(List.of(sido, sigungu));
    }

    @Test
    @DisplayName("지역·타입 필터 없이 modifiedtime만으로 전국을 한 번에 부른다")
    void 전국_단일호출() {
        given(tourApiClient.fetchSyncedPlaces(eq("20260917"), eq(1), anyInt()))
                .willReturn(new AreaBasedSyncPage(List.of(), 0));

        service.syncSince(since);

        verify(tourApiClient).fetchSyncedPlaces(eq("20260917"), eq(1), anyInt());
    }

    @Test
    @DisplayName("showflag=1인 항목은 upsert를 위임한다")
    void 표출항목_upsert() {
        given(tourApiClient.fetchSyncedPlaces(any(), eq(1), anyInt()))
                .willReturn(new AreaBasedSyncPage(List.of(item("c1", "11", "110", "126.97", "37.57", "1")), 1));
        given(placeUpsertService.upsertOne(any(), any(), any(), any(), any())).willReturn(true);

        PlaceIncrementalSyncResult result = service.syncSince(since);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.ignoredDelisted()).isZero();
        verify(placeUpsertService).upsertOne(any(), eq(sigungu), any(), any(), any());
    }

    @Test
    @DisplayName("showflag=0(비표출)은 upsert를 부르지 않고 건너뛴 개수만 센다")
    void 비표출항목_건너뜀() {
        given(tourApiClient.fetchSyncedPlaces(any(), eq(1), anyInt()))
                .willReturn(new AreaBasedSyncPage(List.of(item("c1", "11", "110", "126.97", "37.57", "0")), 1));

        PlaceIncrementalSyncResult result = service.syncSince(since);

        assertThat(result.ignoredDelisted()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(result.skippedContentIds()).isEmpty();
        verify(placeUpsertService, never()).upsertOne(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("좌표가 없으면 건너뛴 목록에 담는다")
    void 좌표없음_건너뜀() {
        given(tourApiClient.fetchSyncedPlaces(any(), eq(1), anyInt()))
                .willReturn(new AreaBasedSyncPage(List.of(item("c1", "11", "110", "", "", "1")), 1));

        PlaceIncrementalSyncResult result = service.syncSince(since);

        assertThat(result.skippedContentIds()).containsExactly("c1");
        verify(placeUpsertService, never()).upsertOne(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("totalCount가 페이지 크기보다 크면 다음 페이지까지 호출한다")
    void 페이지네이션() {
        given(tourApiClient.fetchSyncedPlaces(any(), eq(1), eq(100)))
                .willReturn(new AreaBasedSyncPage(List.of(item("c1", "11", "110", "126.97", "37.57", "1")), 150));
        given(tourApiClient.fetchSyncedPlaces(any(), eq(2), eq(100)))
                .willReturn(new AreaBasedSyncPage(List.of(item("c2", "11", "110", "126.98", "37.58", "1")), 150));
        given(placeUpsertService.upsertOne(any(), any(), any(), any(), any())).willReturn(true);

        PlaceIncrementalSyncResult result = service.syncSince(since);

        assertThat(result.created()).isEqualTo(2);
        verify(tourApiClient).fetchSyncedPlaces(any(), eq(1), eq(100));
        verify(tourApiClient).fetchSyncedPlaces(any(), eq(2), eq(100));
    }

    @Test
    @DisplayName("한 항목 저장이 실패해도 건너뛰고 계속 진행한다")
    void 항목저장_실패해도_계속진행() {
        given(tourApiClient.fetchSyncedPlaces(any(), eq(1), anyInt()))
                .willReturn(new AreaBasedSyncPage(List.of(
                        item("c1", "11", "110", "126.97", "37.57", "1"),
                        item("c2", "11", "110", "126.98", "37.58", "1")), 2));
        given(placeUpsertService.upsertOne(any(), any(), any(), any(), any()))
                .willThrow(new DataIntegrityViolationException("value too long"))
                .willReturn(true);

        PlaceIncrementalSyncResult result = service.syncSince(since);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skippedContentIds()).containsExactly("c1");
    }
}
