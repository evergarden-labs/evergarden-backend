package com.evergarden.evergardenbackend.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.evergarden.evergardenbackend.map.dto.RegionVisitStatus;
import com.evergarden.evergardenbackend.map.repository.RegionVisitAggregate;
import com.evergarden.evergardenbackend.map.repository.RegionVisitRepository;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

/** 전체 지역과 방문 집계를 합쳐 지도 색칠용 목록을 만드는 로직(MAP-02)을 확인한다. */
class RegionVisitServiceTest {

    private static final Long USER_ID = 1L;

    private final RegionVisitRepository regionVisitRepository = mock(RegionVisitRepository.class);
    private final RegionRepository regionRepository = mock(RegionRepository.class);
    private final RegionVisitService service = new RegionVisitService(regionVisitRepository, regionRepository);

    private Region region(String code, RegionLevel level, Region parent) {
        return Region.builder()
                .code(code).parent(parent).level(level).name(code)
                .centerLat(BigDecimal.ONE).centerLng(BigDecimal.ONE).syncedAt(LocalDateTime.now())
                .build();
    }

    private RegionVisitAggregate aggregate(String regionCode, long visitCount, LocalDateTime lastVisitedAt) {
        RegionVisitAggregate a = mock(RegionVisitAggregate.class);
        given(a.getRegionCode()).willReturn(regionCode);
        given(a.getVisitCount()).willReturn(visitCount);
        given(a.getLastVisitedAt()).willReturn(lastVisitedAt);
        return a;
    }

    @Test
    @DisplayName("SIGUNGU 단위로 물으면 방문한 시군구만 visited=true, 나머지는 false")
    void 시군구_단위() {
        Region seoul = region("11", RegionLevel.SIDO, null);
        Region jongno = region("110", RegionLevel.SIGUNGU, seoul);
        Region jung = region("140", RegionLevel.SIGUNGU, seoul);
        given(regionRepository.findAll()).willReturn(List.of(seoul, jongno, jung));
        LocalDateTime visitedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        RegionVisitAggregate jongnoAggregate = aggregate("110", 2, visitedAt);
        given(regionVisitRepository.aggregateByUser(USER_ID)).willReturn(List.of(jongnoAggregate));

        List<RegionVisitStatus> result = service.listMyRegions(USER_ID, RegionLevel.SIGUNGU);

        assertThat(result).hasSize(2);
        RegionVisitStatus jongnoStatus = result.stream().filter(r -> r.region().code().equals("110")).findFirst().orElseThrow();
        assertThat(jongnoStatus.visited()).isTrue();
        assertThat(jongnoStatus.visitCount()).isEqualTo(2);
        assertThat(jongnoStatus.lastVisitedAt()).isEqualTo(visitedAt);

        RegionVisitStatus jungStatus = result.stream().filter(r -> r.region().code().equals("140")).findFirst().orElseThrow();
        assertThat(jungStatus.visited()).isFalse();
        assertThat(jungStatus.visitCount()).isZero();
        assertThat(jungStatus.lastVisitedAt()).isNull();
    }

    @Test
    @DisplayName("SIDO 단위로 물으면 하위 시군구 중 하나라도 방문했으면 시/도도 visited=true, 방문수는 합산")
    void 시도_단위_시군구_방문_합산() {
        Region seoul = region("11", RegionLevel.SIDO, null);
        Region busan = region("26", RegionLevel.SIDO, null);
        Region jongno = region("110", RegionLevel.SIGUNGU, seoul);
        Region jung = region("140", RegionLevel.SIGUNGU, seoul);
        given(regionRepository.findAll()).willReturn(List.of(seoul, busan, jongno, jung));
        LocalDateTime jongnoAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime jungAt = LocalDateTime.of(2026, 1, 5, 10, 0);
        RegionVisitAggregate jongnoAggregate = aggregate("110", 1, jongnoAt);
        RegionVisitAggregate jungAggregate = aggregate("140", 3, jungAt);
        given(regionVisitRepository.aggregateByUser(USER_ID)).willReturn(List.of(jongnoAggregate, jungAggregate));

        List<RegionVisitStatus> result = service.listMyRegions(USER_ID, RegionLevel.SIDO);

        assertThat(result).hasSize(2);
        RegionVisitStatus seoulStatus = result.stream().filter(r -> r.region().code().equals("11")).findFirst().orElseThrow();
        assertThat(seoulStatus.visited()).isTrue();
        assertThat(seoulStatus.visitCount()).isEqualTo(4);
        assertThat(seoulStatus.lastVisitedAt()).isEqualTo(jungAt);

        RegionVisitStatus busanStatus = result.stream().filter(r -> r.region().code().equals("26")).findFirst().orElseThrow();
        assertThat(busanStatus.visited()).isFalse();
        assertThat(busanStatus.visitCount()).isZero();
    }

    @Test
    @DisplayName("방문 기록이 하나도 없으면 전부 visited=false")
    void 방문기록_없음() {
        Region seoul = region("11", RegionLevel.SIDO, null);
        given(regionRepository.findAll()).willReturn(List.of(seoul));
        given(regionVisitRepository.aggregateByUser(USER_ID)).willReturn(List.of());

        List<RegionVisitStatus> result = service.listMyRegions(USER_ID, RegionLevel.SIDO);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).visited()).isFalse();
    }

    // ── 지역 하나 조회(MAP-03) ─────────────────────────────────

    @Test
    @DisplayName("SIGUNGU 지역은 자기 코드만으로 상태를 계산한다")
    void 단건_시군구() {
        Region seoul = region("11", RegionLevel.SIDO, null);
        Region jongno = region("110", RegionLevel.SIGUNGU, seoul);
        LocalDateTime visitedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        RegionVisitAggregate jongnoAggregate = aggregate("110", 2, visitedAt);
        given(regionVisitRepository.aggregateByUser(USER_ID)).willReturn(List.of(jongnoAggregate));

        RegionVisitStatus result = service.getStatus(USER_ID, jongno);

        assertThat(result.visited()).isTrue();
        assertThat(result.visitCount()).isEqualTo(2);
        org.mockito.Mockito.verifyNoInteractions(regionRepository);
    }

    @Test
    @DisplayName("SIDO 지역은 하위 시군구 방문까지 합산한다 — listMyRegions와 같은 규칙")
    void 단건_시도_하위시군구_합산() {
        Region seoul = region("11", RegionLevel.SIDO, null);
        Region jongno = region("110", RegionLevel.SIGUNGU, seoul);
        Region jung = region("140", RegionLevel.SIGUNGU, seoul);
        given(regionRepository.findByParent_Code("11")).willReturn(List.of(jongno, jung));
        LocalDateTime jongnoAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime jungAt = LocalDateTime.of(2026, 1, 5, 10, 0);
        RegionVisitAggregate jongnoAggregate = aggregate("110", 1, jongnoAt);
        RegionVisitAggregate jungAggregate = aggregate("140", 3, jungAt);
        given(regionVisitRepository.aggregateByUser(USER_ID)).willReturn(List.of(jongnoAggregate, jungAggregate));

        RegionVisitStatus result = service.getStatus(USER_ID, seoul);

        assertThat(result.visited()).isTrue();
        assertThat(result.visitCount()).isEqualTo(4);
        assertThat(result.lastVisitedAt()).isEqualTo(jungAt);
    }
}
