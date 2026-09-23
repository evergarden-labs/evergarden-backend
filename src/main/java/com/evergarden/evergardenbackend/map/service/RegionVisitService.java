package com.evergarden.evergardenbackend.map.service;

import com.evergarden.evergardenbackend.garden.dto.GardenObjectResponse;
import com.evergarden.evergardenbackend.garden.entity.GardenObject;
import com.evergarden.evergardenbackend.garden.entity.RewardStatus;
import com.evergarden.evergardenbackend.garden.entity.UserGardenObject;
import com.evergarden.evergardenbackend.garden.repository.GardenObjectRepository;
import com.evergarden.evergardenbackend.garden.repository.UserGardenObjectRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.map.dto.RegionVisitRequest;
import com.evergarden.evergardenbackend.map.dto.RegionVisitResult;
import com.evergarden.evergardenbackend.map.dto.RegionVisitStatus;
import com.evergarden.evergardenbackend.map.dto.VisitReward;
import com.evergarden.evergardenbackend.map.entity.RegionVisit;
import com.evergarden.evergardenbackend.map.repository.RegionVisitAggregate;
import com.evergarden.evergardenbackend.map.repository.RegionVisitRepository;
import com.evergarden.evergardenbackend.place.dto.RegionSummary;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지역 방문 인증·조회(MAP-01·02·03, GARDEN-02). */
@Service
@RequiredArgsConstructor
@Transactional
public class RegionVisitService {

    private static final double ACCURACY_THRESHOLD_METERS = 100;
    private static final int COOLDOWN_DAYS = 7;

    private final RegionVisitRepository regionVisitRepository;
    private final RegionRepository regionRepository;
    private final GardenObjectRepository gardenObjectRepository;
    private final UserGardenObjectRepository userGardenObjectRepository;
    private final UserRepository userRepository;
    private final RegionDeterminationService regionDeterminationService;
    private final RegionVisitAccessGuard accessGuard;

    /**
     * 지역 인증(MAP-01). 순서가 중요하다 — ① 정확도부터 걸러 콘텐츠랩 호출을 아끼고,
     * ② 좌표로 지역을 판정하고, ③ 앱이 같이 보낸 {@code regionCode}와 대조한다.
     * 쿨다운은 인증 자체를 막지 않는다(ADR-038) — 정원 보상만 건너뛰고 방문 기록은
     * 항상 남는다.
     */
    public RegionVisitResult verify(Long userId, RegionVisitRequest request) {
        if (request.accuracyMeters() > ACCURACY_THRESHOLD_METERS) {
            throw new BusinessException(ErrorCode.LOCATION_ACCURACY_TOO_LOW);
        }

        BigDecimal lat = BigDecimal.valueOf(request.lat());
        BigDecimal lng = BigDecimal.valueOf(request.lng());
        Region region = regionDeterminationService.determine(lat, lng);
        if (request.regionCode() != null && !request.regionCode().equals(region.getCode())) {
            throw new BusinessException(ErrorCode.LOCATION_MISMATCH);
        }

        User user = userRepository.getReferenceById(userId);
        boolean isFirstVisit = !regionVisitRepository.existsByUser_IdAndRegion_Code(userId, region.getCode());
        LocalDateTime now = LocalDateTime.now();
        RewardOutcome outcome = applyReward(user, region, now);

        RegionVisit visit = RegionVisit.builder()
                .user(user).region(region).lat(lat).lng(lng)
                .accuracyMeters(BigDecimal.valueOf(request.accuracyMeters()))
                .verifiedAt(now)
                .rewardStatus(outcome.status())
                .gardenObject(outcome.gardenObject())
                .previousStage(outcome.previousStage())
                .currentStage(outcome.currentStage())
                .nextAvailableAt(outcome.nextAvailableAt())
                .build();
        regionVisitRepository.save(visit);

        VisitReward reward = outcome.status() == RewardStatus.NONE ? null : toVisitReward(visit);
        return new RegionVisitResult(visit.getId(), RegionSummary.of(region), now, isFirstVisit, outcome.status(), reward);
    }

    /**
     * 인증 한 건의 보상 결과를 다시 본다(GARDEN-02). 인증 시점에 계산해 저장해 둔
     * 스냅샷을 그대로 보여준다 — 재계산하지 않는다. 그사이 재방문으로 정원이 더
     * 자랐어도 "그때 이 인증이 남긴 결과"는 안 바뀌어야 해서다.
     */
    @Transactional(readOnly = true)
    public VisitReward getVisitReward(Long userId, Long visitId) {
        RegionVisit visit = regionVisitRepository.findById(visitId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REGION_VISIT_NOT_FOUND));
        accessGuard.checkOwner(visit, userId);
        return toVisitReward(visit);
    }

    private VisitReward toVisitReward(RegionVisit visit) {
        GardenObjectResponse gardenObject = visit.getGardenObject() != null
                ? GardenObjectResponse.of(visit.getGardenObject()) : null;
        return new VisitReward(visit.getId(), RegionSummary.of(visit.getRegion()), visit.getRewardStatus(),
                gardenObject, toInt(visit.getPreviousStage()), toInt(visit.getCurrentStage()), visit.getNextAvailableAt());
    }

    private Integer toInt(Short value) {
        return value == null ? null : value.intValue();
    }

    /**
     * 정원 보상 계산(GARDEN-02). 그 지역에 지정된 오브젝트가 없으면 {@code NONE}.
     * 있으면 처음 해금이면 {@code UNLOCKED}, 이미 있으면 {@code growIfPossible()}로
     * 자랐으면 {@code GROWN}, 못 자랐으면(쿨다운 중이거나 이미 최대 단계) {@code COOLDOWN} —
     * "이미 최대 단계"를 위한 별도 상태가 명세에 없어 같은 코드로 합쳤다(이땐
     * {@code nextAvailableAt}이 {@code null}이라 "다음이 없다"는 뜻으로 구분된다).
     */
    private RewardOutcome applyReward(User user, Region region, LocalDateTime now) {
        Optional<GardenObject> assigned = gardenObjectRepository.findByRegion_Code(region.getCode()).stream().findFirst();
        if (assigned.isEmpty()) {
            return new RewardOutcome(RewardStatus.NONE, null, null, null, null);
        }
        GardenObject gardenObject = assigned.get();

        Optional<UserGardenObject> existing =
                userGardenObjectRepository.findByUser_IdAndGardenObject_Id(user.getId(), gardenObject.getId());
        if (existing.isEmpty()) {
            int inserted = userGardenObjectRepository.tryInsertUnlock(user.getId(), gardenObject.getId(), now);
            if (inserted == 1) {
                UserGardenObject unlocked = userGardenObjectRepository
                        .findByUser_IdAndGardenObject_Id(user.getId(), gardenObject.getId())
                        .orElseThrow(() -> new IllegalStateException(
                                "방금 해금 삽입에 성공했는데 다시 못 찾음: gardenObjectId=" + gardenObject.getId()));
                return new RewardOutcome(RewardStatus.UNLOCKED, gardenObject, null, unlocked.getStage(), null);
            }
            // 동시에 들어온 다른 요청이 먼저 해금했다(inserted == 0) — 그 행을 읽어 성장 판정으로 이어간다.
            existing = userGardenObjectRepository.findByUser_IdAndGardenObject_Id(user.getId(), gardenObject.getId());
        }

        UserGardenObject userGardenObject = existing.orElseThrow(() -> new IllegalStateException(
                "해금 경합 직후에도 UserGardenObject를 찾지 못함: gardenObjectId=" + gardenObject.getId()));
        short beforeStage = userGardenObject.getStage();
        if (userGardenObject.growIfPossible(now, COOLDOWN_DAYS)) {
            return new RewardOutcome(RewardStatus.GROWN, gardenObject, beforeStage, userGardenObject.getStage(), null);
        }
        LocalDateTime nextAvailableAt = beforeStage >= gardenObject.getMaxStage()
                ? null : userGardenObject.nextGrowableAt(COOLDOWN_DAYS);
        return new RewardOutcome(RewardStatus.COOLDOWN, gardenObject, null, beforeStage, nextAvailableAt);
    }

    private record RewardOutcome(RewardStatus status, GardenObject gardenObject,
                                  Short previousStage, Short currentStage, LocalDateTime nextAvailableAt) {
    }

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
