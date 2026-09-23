package com.evergarden.evergardenbackend.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.garden.entity.GardenObject;
import com.evergarden.evergardenbackend.garden.entity.GardenObjectType;
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
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** 전체 지역과 방문 집계를 합쳐 지도 색칠용 목록을 만드는 로직(MAP-02)을 확인한다. */
class RegionVisitServiceTest {

    private static final Long USER_ID = 1L;

    private final RegionVisitRepository regionVisitRepository = mock(RegionVisitRepository.class);
    private final RegionRepository regionRepository = mock(RegionRepository.class);
    private final GardenObjectRepository gardenObjectRepository = mock(GardenObjectRepository.class);
    private final UserGardenObjectRepository userGardenObjectRepository = mock(UserGardenObjectRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RegionDeterminationService regionDeterminationService = mock(RegionDeterminationService.class);
    private final RegionVisitAccessGuard accessGuard = mock(RegionVisitAccessGuard.class);
    private final RegionVisitService service = new RegionVisitService(
            regionVisitRepository, regionRepository, gardenObjectRepository, userGardenObjectRepository,
            userRepository, regionDeterminationService, accessGuard);

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

    // ── 지역 인증(MAP-01) ──────────────────────────────────────

    private User user(Long id) {
        User user = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private GardenObject gardenObject(Long id, short maxStage) {
        GardenObject gardenObject = GardenObject.builder()
                .name("종로 나무").type(GardenObjectType.PLANT).maxStage(maxStage).build();
        ReflectionTestUtils.setField(gardenObject, "id", id);
        return gardenObject;
    }

    private RegionVisitRequest request(double lat, double lng, double accuracy, String regionCode) {
        return new RegionVisitRequest(lat, lng, accuracy, regionCode);
    }

    @Test
    @DisplayName("정확도가 100m를 넘으면 LOCATION_ACCURACY_TOO_LOW — 지역 판정을 시도하지 않는다")
    void 인증_정확도초과() {
        assertThatThrownBy(() -> service.verify(USER_ID, request(37.5, 127.0, 100.1, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOCATION_ACCURACY_TOO_LOW);
        verify(regionDeterminationService, never()).determine(any(), any());
    }

    @Test
    @DisplayName("판정된 지역코드가 요청의 regionCode와 다르면 LOCATION_MISMATCH")
    void 인증_지역코드불일치() {
        Region jongno = region("110", RegionLevel.SIGUNGU, null);
        given(regionDeterminationService.determine(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0)))
                .willReturn(jongno);

        assertThatThrownBy(() -> service.verify(USER_ID, request(37.5, 127.0, 50.0, "140")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOCATION_MISMATCH);
        verify(regionVisitRepository, never()).save(any());
    }

    @Test
    @DisplayName("그 지역에 지정된 오브젝트가 없으면 NONE — reward는 null이고 방문 기록만 남는다")
    void 인증_오브젝트없음() {
        Region jongno = region("110", RegionLevel.SIGUNGU, null);
        given(regionDeterminationService.determine(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0)))
                .willReturn(jongno);
        given(userRepository.getReferenceById(USER_ID)).willReturn(user(USER_ID));
        given(regionVisitRepository.existsByUser_IdAndRegion_Code(USER_ID, "110")).willReturn(false);
        given(gardenObjectRepository.findByRegion_Code("110")).willReturn(List.of());
        given(regionVisitRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        RegionVisitResult result = service.verify(USER_ID, request(37.5, 127.0, 50.0, null));

        assertThat(result.rewardStatus()).isEqualTo(RewardStatus.NONE);
        assertThat(result.reward()).isNull();
        assertThat(result.isFirstVisit()).isTrue();
        verify(userGardenObjectRepository, never()).save(any());
    }

    @Test
    @DisplayName("처음 해금이면 UNLOCKED — tryInsertUnlock()으로 새로 만든다")
    void 인증_처음해금() {
        Region jongno = region("110", RegionLevel.SIGUNGU, null);
        User user = user(USER_ID);
        GardenObject tree = gardenObject(1L, (short) 3);
        UserGardenObject unlocked = UserGardenObject.builder()
                .user(user).gardenObject(tree).unlockedAt(LocalDateTime.now()).build();
        given(regionDeterminationService.determine(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0)))
                .willReturn(jongno);
        given(userRepository.getReferenceById(USER_ID)).willReturn(user);
        given(regionVisitRepository.existsByUser_IdAndRegion_Code(USER_ID, "110")).willReturn(true);
        given(gardenObjectRepository.findByRegion_Code("110")).willReturn(List.of(tree));
        given(userGardenObjectRepository.findByUser_IdAndGardenObject_Id(USER_ID, 1L))
                .willReturn(Optional.empty(), Optional.of(unlocked));
        given(userGardenObjectRepository.tryInsertUnlock(eq(USER_ID), eq(1L), any())).willReturn(1);
        given(regionVisitRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        RegionVisitResult result = service.verify(USER_ID, request(37.5, 127.0, 50.0, null));

        assertThat(result.rewardStatus()).isEqualTo(RewardStatus.UNLOCKED);
        assertThat(result.reward()).isNotNull();
        assertThat(result.reward().previousStage()).isNull();
        assertThat(result.reward().currentStage()).isEqualTo(1);
        verify(userGardenObjectRepository).tryInsertUnlock(eq(USER_ID), eq(1L), any());
    }

    @Test
    @DisplayName("동시에 다른 요청이 먼저 해금했으면(tryInsertUnlock == 0) 다시 읽어서 성장 판정으로 이어간다")
    void 인증_처음해금_경합에서짐() {
        Region jongno = region("110", RegionLevel.SIGUNGU, null);
        User user = user(USER_ID);
        GardenObject tree = gardenObject(1L, (short) 3);
        UserGardenObject wonByOther = UserGardenObject.builder()
                .user(user).gardenObject(tree).unlockedAt(LocalDateTime.now().minusDays(10)).build();
        given(regionDeterminationService.determine(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0)))
                .willReturn(jongno);
        given(userRepository.getReferenceById(USER_ID)).willReturn(user);
        given(regionVisitRepository.existsByUser_IdAndRegion_Code(USER_ID, "110")).willReturn(true);
        given(gardenObjectRepository.findByRegion_Code("110")).willReturn(List.of(tree));
        given(userGardenObjectRepository.findByUser_IdAndGardenObject_Id(USER_ID, 1L))
                .willReturn(Optional.empty(), Optional.of(wonByOther));
        given(userGardenObjectRepository.tryInsertUnlock(eq(USER_ID), eq(1L), any())).willReturn(0);
        given(regionVisitRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        RegionVisitResult result = service.verify(USER_ID, request(37.5, 127.0, 50.0, null));

        assertThat(result.rewardStatus()).isEqualTo(RewardStatus.GROWN);
        verify(userGardenObjectRepository, org.mockito.Mockito.times(2))
                .findByUser_IdAndGardenObject_Id(USER_ID, 1L);
    }

    @Test
    @DisplayName("쿨다운이 지났으면 GROWN — 단계가 하나 올라간다")
    void 인증_성장() {
        Region jongno = region("110", RegionLevel.SIGUNGU, null);
        User user = user(USER_ID);
        GardenObject tree = gardenObject(1L, (short) 3);
        UserGardenObject existing = UserGardenObject.builder()
                .user(user).gardenObject(tree).unlockedAt(LocalDateTime.now().minusDays(10)).build();
        ReflectionTestUtils.setField(existing, "id", 5L);
        ReflectionTestUtils.setField(existing, "lastGrownAt", LocalDateTime.now().minusDays(8));
        given(regionDeterminationService.determine(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0)))
                .willReturn(jongno);
        given(userRepository.getReferenceById(USER_ID)).willReturn(user);
        given(regionVisitRepository.existsByUser_IdAndRegion_Code(USER_ID, "110")).willReturn(true);
        given(gardenObjectRepository.findByRegion_Code("110")).willReturn(List.of(tree));
        given(userGardenObjectRepository.findByUser_IdAndGardenObject_Id(USER_ID, 1L))
                .willReturn(Optional.of(existing));
        given(regionVisitRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        RegionVisitResult result = service.verify(USER_ID, request(37.5, 127.0, 50.0, null));

        assertThat(result.rewardStatus()).isEqualTo(RewardStatus.GROWN);
        assertThat(result.reward().previousStage()).isEqualTo(1);
        assertThat(result.reward().currentStage()).isEqualTo(2);
    }

    @Test
    @DisplayName("7일 안에 재인증이면 COOLDOWN — 방문 기록은 남지만 안 자란다")
    void 인증_쿨다운() {
        Region jongno = region("110", RegionLevel.SIGUNGU, null);
        User user = user(USER_ID);
        GardenObject tree = gardenObject(1L, (short) 3);
        UserGardenObject existing = UserGardenObject.builder()
                .user(user).gardenObject(tree).unlockedAt(LocalDateTime.now().minusDays(3)).build();
        ReflectionTestUtils.setField(existing, "id", 5L);
        ReflectionTestUtils.setField(existing, "lastGrownAt", LocalDateTime.now().minusDays(1));
        given(regionDeterminationService.determine(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0)))
                .willReturn(jongno);
        given(userRepository.getReferenceById(USER_ID)).willReturn(user);
        given(regionVisitRepository.existsByUser_IdAndRegion_Code(USER_ID, "110")).willReturn(true);
        given(gardenObjectRepository.findByRegion_Code("110")).willReturn(List.of(tree));
        given(userGardenObjectRepository.findByUser_IdAndGardenObject_Id(USER_ID, 1L))
                .willReturn(Optional.of(existing));
        ArgumentCaptor<RegionVisit> captor = ArgumentCaptor.forClass(RegionVisit.class);
        given(regionVisitRepository.save(captor.capture())).willAnswer(inv -> inv.getArgument(0));

        RegionVisitResult result = service.verify(USER_ID, request(37.5, 127.0, 50.0, null));

        assertThat(result.rewardStatus()).isEqualTo(RewardStatus.COOLDOWN);
        assertThat(result.reward().nextAvailableAt()).isEqualTo(existing.getLastGrownAt().plusDays(7));
        assertThat(captor.getValue()).isNotNull();
    }

    // ── 보상 다시 보기(GARDEN-02) ────────────────────────────

    @Test
    @DisplayName("없는 방문이면 REGION_VISIT_NOT_FOUND")
    void 보상조회_없는방문() {
        given(regionVisitRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVisitReward(USER_ID, 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REGION_VISIT_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 방문이면 403 — 접근가드에 위임한다")
    void 보상조회_남의방문() {
        RegionVisit visit = mock(RegionVisit.class);
        given(regionVisitRepository.findById(5L)).willReturn(Optional.of(visit));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.NOT_RESOURCE_OWNER))
                .when(accessGuard).checkOwner(visit, USER_ID);

        assertThatThrownBy(() -> service.getVisitReward(USER_ID, 5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_RESOURCE_OWNER);
    }

    @Test
    @DisplayName("정상 조회는 저장된 스냅샷을 그대로 돌려준다")
    void 보상조회_정상() {
        Region jongno = region("110", RegionLevel.SIGUNGU, null);
        GardenObject tree = gardenObject(1L, (short) 3);
        RegionVisit visit = RegionVisit.builder()
                .user(user(USER_ID)).region(jongno)
                .lat(BigDecimal.ONE).lng(BigDecimal.ONE).accuracyMeters(BigDecimal.TEN)
                .verifiedAt(LocalDateTime.now())
                .rewardStatus(RewardStatus.GROWN).gardenObject(tree)
                .previousStage((short) 1).currentStage((short) 2).build();
        ReflectionTestUtils.setField(visit, "id", 5L);
        given(regionVisitRepository.findById(5L)).willReturn(Optional.of(visit));

        VisitReward reward = service.getVisitReward(USER_ID, 5L);

        assertThat(reward.visitId()).isEqualTo(5L);
        assertThat(reward.rewardStatus()).isEqualTo(RewardStatus.GROWN);
        assertThat(reward.previousStage()).isEqualTo(1);
        assertThat(reward.currentStage()).isEqualTo(2);
    }
}
