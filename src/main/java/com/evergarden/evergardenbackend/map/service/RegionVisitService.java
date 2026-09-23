package com.evergarden.evergardenbackend.map.service;

import com.evergarden.evergardenbackend.map.dto.RegionVisitStatus;
import com.evergarden.evergardenbackend.map.repository.RegionVisitAggregate;
import com.evergarden.evergardenbackend.map.repository.RegionVisitRepository;
import com.evergarden.evergardenbackend.place.dto.RegionSummary;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지역 방문 조회(MAP-02 이하). */
@Service
@RequiredArgsConstructor
@Transactional
public class RegionVisitService {

    private final RegionVisitRepository regionVisitRepository;
    private final RegionRepository regionRepository;

    /**
     * 방문 지역 확인(MAP-02). 방문한 지역만이 아니라 전체 지역을 {@code visited}로 구분해
     * 반환한다 — 앱이 미방문 지역까지 알아야 지도를 색칠할 수 있다.
     *
     * <p>{@code SIDO} 단위로 물으면 그 시/도에 속한 시군구 중 하나라도 방문했으면 시/도
     * 자체도 방문한 것으로 합산한다 — 사용자는 "그 시군구"가 아니라 "그 도시"를 다녀왔다고
     * 인식하고, MAP-02가 요구하는 "시각적으로 구분되어 표시"도 시/도 지도에서부터 보여야
     * 자연스럽다. {@code RegionVisit}은 인증 시점에 판정된 시군구 단위로만 쌓인다.
     */
    @Transactional(readOnly = true)
    public List<RegionVisitStatus> listMyRegions(Long userId, RegionLevel level) {
        List<Region> allRegions = regionRepository.findAll();
        Map<String, RegionVisitAggregate> aggregates = regionVisitRepository.aggregateByUser(userId).stream()
                .collect(Collectors.toMap(RegionVisitAggregate::getRegionCode, a -> a));

        List<Region> targets = allRegions.stream().filter(r -> r.getLevel() == level).toList();
        if (level == RegionLevel.SIGUNGU) {
            return targets.stream().map(region -> toStatus(region, List.of(region.getCode()), aggregates)).toList();
        }

        Map<String, List<Region>> childrenBySido = allRegions.stream()
                .filter(r -> r.getLevel() == RegionLevel.SIGUNGU)
                .collect(Collectors.groupingBy(r -> r.getParent().getCode()));
        return targets.stream().map(sido -> {
            List<String> codes = new ArrayList<>();
            codes.add(sido.getCode());
            childrenBySido.getOrDefault(sido.getCode(), List.of()).forEach(c -> codes.add(c.getCode()));
            return toStatus(sido, codes, aggregates);
        }).toList();
    }

    /**
     * 지역 하나의 방문 상태(MAP-03의 {@code getRegion}이 쓴다). {@link #listMyRegions}와
     * 같은 {@code SIDO} 합산 규칙을 쓴다 — 지도 화면과 지역 상세 화면에서 같은 도시가
     * 다르게 보이면 안 된다.
     */
    @Transactional(readOnly = true)
    public RegionVisitStatus getStatus(Long userId, Region region) {
        List<String> codes = new ArrayList<>();
        codes.add(region.getCode());
        if (region.getLevel() == RegionLevel.SIDO) {
            regionRepository.findByParent_Code(region.getCode()).forEach(child -> codes.add(child.getCode()));
        }
        Map<String, RegionVisitAggregate> aggregates = regionVisitRepository.aggregateByUser(userId).stream()
                .collect(Collectors.toMap(RegionVisitAggregate::getRegionCode, a -> a));
        return toStatus(region, codes, aggregates);
    }

    private RegionVisitStatus toStatus(Region region, List<String> aggregatedCodes,
                                        Map<String, RegionVisitAggregate> aggregates) {
        long visitCount = aggregatedCodes.stream()
                .map(aggregates::get).filter(Objects::nonNull)
                .mapToLong(RegionVisitAggregate::getVisitCount).sum();
        LocalDateTime lastVisitedAt = aggregatedCodes.stream()
                .map(aggregates::get).filter(Objects::nonNull)
                .map(RegionVisitAggregate::getLastVisitedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new RegionVisitStatus(RegionSummary.of(region), visitCount > 0, (int) visitCount, lastVisitedAt);
    }
}
