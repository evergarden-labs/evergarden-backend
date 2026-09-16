package com.evergarden.evergardenbackend.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.place.client.KakaoLocalClient;
import com.evergarden.evergardenbackend.place.client.TourApiClient;
import com.evergarden.evergardenbackend.place.client.dto.Coordinate;
import com.evergarden.evergardenbackend.place.client.dto.RegionCode;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 법정동(TourAPI) + 좌표(카카오)를 합쳐 Region을 시딩하는 흐름. */
class RegionSyncServiceTest {

    private static final Coordinate SEOUL = new Coordinate(new BigDecimal("37.5666103"), new BigDecimal("126.9783882"));
    private static final Coordinate JONGNO = new Coordinate(new BigDecimal("37.5729"), new BigDecimal("126.9794"));
    private static final Coordinate JUNG = new Coordinate(new BigDecimal("37.5641"), new BigDecimal("126.9979"));

    private final TourApiClient tourApiClient = mock(TourApiClient.class);
    private final KakaoLocalClient kakaoLocalClient = mock(KakaoLocalClient.class);
    private final RegionRepository regionRepository = mock(RegionRepository.class);
    private final RegionSyncService service =
            new RegionSyncService(tourApiClient, kakaoLocalClient, regionRepository);

    @Test
    @DisplayName("시/도·시군구 좌표를 다 찾으면 부모-자식 관계까지 저장한다")
    void 시딩_기본_흐름() {
        given(tourApiClient.fetchProvinces()).willReturn(List.of(new RegionCode("11", "서울특별시")));
        given(tourApiClient.fetchDistricts("11")).willReturn(List.of(
                new RegionCode("110", "종로구"), new RegionCode("140", "중구")));
        given(kakaoLocalClient.searchAddress("서울특별시")).willReturn(Optional.of(SEOUL));
        given(kakaoLocalClient.searchAddress("서울특별시 종로구")).willReturn(Optional.of(JONGNO));
        given(kakaoLocalClient.searchAddress("서울특별시 중구")).willReturn(Optional.of(JUNG));
        given(regionRepository.findById(org.mockito.ArgumentMatchers.anyString())).willReturn(Optional.empty());
        given(regionRepository.save(org.mockito.ArgumentMatchers.any(Region.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        RegionSyncResult result = service.syncAll();

        assertThat(result.provinceCount()).isEqualTo(1);
        assertThat(result.districtCount()).isEqualTo(2);
        assertThat(result.failedQueries()).isEmpty();

        ArgumentCaptor<Region> captor = ArgumentCaptor.forClass(Region.class);
        verify(regionRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        List<Region> saved = captor.getAllValues();

        Region seoul = saved.get(0);
        assertThat(seoul.getCode()).isEqualTo("11");
        assertThat(seoul.getLevel()).isEqualTo(RegionLevel.SIDO);
        assertThat(seoul.getParent()).isNull();
        assertThat(seoul.getCenterLat()).isEqualTo(SEOUL.lat());

        Region jongno = saved.get(1);
        assertThat(jongno.getCode()).isEqualTo("11110");
        assertThat(jongno.getLevel()).isEqualTo(RegionLevel.SIGUNGU);
        assertThat(jongno.getParent()).isSameAs(seoul);
        assertThat(jongno.getCenterLat()).isEqualTo(JONGNO.lat());
    }

    @Test
    @DisplayName("이미 있는 지역이면 새로 만들지 않고 좌표·이름을 갱신한다")
    void 이미_있는_지역은_갱신() {
        Region existing = Region.builder()
                .code("11").level(RegionLevel.SIDO).name("옛이름")
                .centerLat(BigDecimal.ZERO).centerLng(BigDecimal.ZERO)
                .syncedAt(LocalDateTime.now().minusYears(1))
                .build();

        given(tourApiClient.fetchProvinces()).willReturn(List.of(new RegionCode("11", "서울특별시")));
        given(tourApiClient.fetchDistricts("11")).willReturn(List.of());
        given(kakaoLocalClient.searchAddress("서울특별시")).willReturn(Optional.of(SEOUL));
        given(regionRepository.findById("11")).willReturn(Optional.of(existing));

        service.syncAll();

        assertThat(existing.getName()).isEqualTo("서울특별시");
        assertThat(existing.getCenterLat()).isEqualTo(SEOUL.lat());
        verify(regionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("시/도 좌표를 못 찾으면 그 시/도의 시군구는 아예 조회하지 않는다")
    void 시도_좌표_없으면_시군구_건너뜀() {
        given(tourApiClient.fetchProvinces()).willReturn(List.of(new RegionCode("11", "서울특별시")));
        given(kakaoLocalClient.searchAddress("서울특별시")).willReturn(Optional.empty());

        RegionSyncResult result = service.syncAll();

        assertThat(result.provinceCount()).isZero();
        assertThat(result.districtCount()).isZero();
        assertThat(result.failedQueries()).containsExactly("서울특별시");
        verify(tourApiClient, never()).fetchDistricts(eq("11"));
    }

    @Test
    @DisplayName("시군구 좌표만 못 찾으면 그 시군구만 건너뛰고 나머지는 저장한다")
    void 시군구_좌표만_없으면_그것만_건너뜀() {
        given(tourApiClient.fetchProvinces()).willReturn(List.of(new RegionCode("11", "서울특별시")));
        given(tourApiClient.fetchDistricts("11")).willReturn(List.of(
                new RegionCode("110", "종로구"), new RegionCode("140", "중구")));
        given(kakaoLocalClient.searchAddress("서울특별시")).willReturn(Optional.of(SEOUL));
        given(kakaoLocalClient.searchAddress("서울특별시 종로구")).willReturn(Optional.empty());
        given(kakaoLocalClient.searchAddress("서울특별시 중구")).willReturn(Optional.of(JUNG));
        given(regionRepository.findById(org.mockito.ArgumentMatchers.anyString())).willReturn(Optional.empty());
        given(regionRepository.save(org.mockito.ArgumentMatchers.any(Region.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        RegionSyncResult result = service.syncAll();

        assertThat(result.provinceCount()).isEqualTo(1);
        assertThat(result.districtCount()).isEqualTo(1);
        assertThat(result.failedQueries()).containsExactly("서울특별시 종로구");
    }
}
