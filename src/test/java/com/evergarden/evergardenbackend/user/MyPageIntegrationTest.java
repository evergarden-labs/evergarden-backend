package com.evergarden.evergardenbackend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evergarden.evergardenbackend.archive.entity.Archive;
import com.evergarden.evergardenbackend.archive.entity.ArchiveTheme;
import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.community.entity.Post;
import com.evergarden.evergardenbackend.community.entity.ShareType;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.garden.entity.GardenObject;
import com.evergarden.evergardenbackend.garden.entity.GardenObjectType;
import com.evergarden.evergardenbackend.garden.entity.RewardStatus;
import com.evergarden.evergardenbackend.garden.entity.UserGardenObject;
import com.evergarden.evergardenbackend.garden.repository.GardenObjectRepository;
import com.evergarden.evergardenbackend.garden.repository.UserGardenObjectRepository;
import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.map.entity.RegionVisit;
import com.evergarden.evergardenbackend.map.repository.RegionVisitRepository;
import com.evergarden.evergardenbackend.place.entity.Region;
import com.evergarden.evergardenbackend.place.entity.RegionLevel;
import com.evergarden.evergardenbackend.place.repository.RegionRepository;
import com.evergarden.evergardenbackend.support.IntegrationTest;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsule;
import com.evergarden.evergardenbackend.timecapsule.repository.TimeCapsuleRepository;
import com.evergarden.evergardenbackend.trip.entity.Trip;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 PostgreSQL로 여러 도메인에 데이터를 심어두고 {@code MyStats} 집계가
 * 정확한지, 프로필 수정의 닉네임 유니크 제약이 실제로 걸리는지 확인한다.
 *
 * <p>{@code gardenTotalCount}는 전체 사용자를 통틀어 세는 값이라({@code GardenObjectRepository.count()})
 * 다른 테스트(특히 {@code @Transactional}이 없어 롤백되지 않는 동시성 테스트들)가 남긴
 * {@code GardenObject}가 섞일 수 있다 — 절대값이 아니라 "이 테스트가 만든 만큼 늘었는지"로 확인한다.
 */
@AutoConfigureMockMvc
@Transactional
class MyPageIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired ArchiveRepository archiveRepository;
    @Autowired TripRepository tripRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired RegionVisitRepository regionVisitRepository;
    @Autowired GardenObjectRepository gardenObjectRepository;
    @Autowired UserGardenObjectRepository userGardenObjectRepository;
    @Autowired PostRepository postRepository;
    @Autowired TimeCapsuleRepository timeCapsuleRepository;

    @Test
    @DisplayName("여러 도메인에 걸친 기록을 실제로 만들어 두면 MyStats가 정확히 맞는다")
    void 프로필조회_지표정확성() throws Exception {
        User user = userRepository.save(User.builder().nickname("여행자9001").build());
        String accessToken = tokenProvider.issueAccessToken(user.getId(), Role.USER);
        long gardenTotalBefore = gardenObjectRepository.count();

        // 기록 — 아카이브 1, 트립 1
        archiveRepository.save(Archive.builder().owner(user).title("제주 여행").theme(ArchiveTheme.BOOK).build());
        tripRepository.save(Trip.builder().owner(user).title("제주도")
                .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(2)).build());

        // 지도 — 서로 다른 지역 2곳 인증
        Region seoul = regionRepository.save(Region.builder()
                .code("11").level(RegionLevel.SIDO).name("서울특별시")
                .centerLat(new BigDecimal("37.5665")).centerLng(new BigDecimal("126.9780"))
                .syncedAt(LocalDateTime.now()).build());
        Region busan = regionRepository.save(Region.builder()
                .code("26").level(RegionLevel.SIDO).name("부산광역시")
                .centerLat(new BigDecimal("35.1796")).centerLng(new BigDecimal("129.0756"))
                .syncedAt(LocalDateTime.now()).build());
        regionVisitRepository.save(RegionVisit.builder()
                .user(user).region(seoul).lat(seoul.getCenterLat()).lng(seoul.getCenterLng())
                .verifiedAt(LocalDateTime.now()).rewardStatus(RewardStatus.NONE).build());
        regionVisitRepository.save(RegionVisit.builder()
                .user(user).region(busan).lat(busan.getCenterLat()).lng(busan.getCenterLng())
                .verifiedAt(LocalDateTime.now()).rewardStatus(RewardStatus.NONE).build());

        // 정원 — 오브젝트 하나 해금
        GardenObject tree = gardenObjectRepository.save(GardenObject.builder()
                .region(seoul).name("서울 은행나무").type(GardenObjectType.PLANT).maxStage((short) 3).build());
        userGardenObjectRepository.save(
                UserGardenObject.builder().user(user).gardenObject(tree).unlockedAt(LocalDateTime.now()).build());

        // 커뮤니티 — 게시물 1개 + 좋아요 3
        Post post = postRepository.save(Post.builder()
                .author(user).content("제주 다녀왔어요").shareType(ShareType.COURSE).build());
        post.increaseLikeCount();
        post.increaseLikeCount();
        post.increaseLikeCount();
        postRepository.saveAndFlush(post);

        // 타임캡슐 — 봉인 1개
        timeCapsuleRepository.save(TimeCapsule.sealUntilDate(
                user, "내년의 나에게", "내용", LocalDate.now().plusYears(1)));

        mvc.perform(get("/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats.archiveCount").value(1))
                .andExpect(jsonPath("$.data.stats.tripCount").value(1))
                .andExpect(jsonPath("$.data.stats.visitedRegionCount").value(2))
                .andExpect(jsonPath("$.data.stats.gardenUnlockedCount").value(1))
                .andExpect(jsonPath("$.data.stats.gardenTotalCount").value(gardenTotalBefore + 1))
                .andExpect(jsonPath("$.data.stats.postCount").value(1))
                .andExpect(jsonPath("$.data.stats.receivedLikeCount").value(3))
                .andExpect(jsonPath("$.data.stats.sealedCapsuleCount").value(1))
                .andExpect(jsonPath("$.data.stats.unlockableCapsuleCount").value(0));
    }

    @Test
    @DisplayName("기록이 하나도 없는 신규 사용자는 아홉 개 지표가 전부 0이다")
    void 프로필조회_신규사용자_전부0() throws Exception {
        User user = userRepository.save(User.builder().nickname("여행자9002").build());
        String accessToken = tokenProvider.issueAccessToken(user.getId(), Role.USER);

        mvc.perform(get("/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats.archiveCount").value(0))
                .andExpect(jsonPath("$.data.stats.tripCount").value(0))
                .andExpect(jsonPath("$.data.stats.visitedRegionCount").value(0))
                .andExpect(jsonPath("$.data.stats.gardenUnlockedCount").value(0))
                .andExpect(jsonPath("$.data.stats.postCount").value(0))
                .andExpect(jsonPath("$.data.stats.receivedLikeCount").value(0))
                .andExpect(jsonPath("$.data.stats.sealedCapsuleCount").value(0))
                .andExpect(jsonPath("$.data.stats.unlockableCapsuleCount").value(0));
    }

    @Test
    @DisplayName("이미 쓰이고 있는 닉네임으로 수정하면 NICKNAME_DUPLICATED — 실제 유니크 제약으로 확인")
    void 프로필수정_닉네임중복() throws Exception {
        userRepository.save(User.builder().nickname("먼저씀").build());
        User user = userRepository.save(User.builder().nickname("여행자9003").build());
        String accessToken = tokenProvider.issueAccessToken(user.getId(), Role.USER);

        mvc.perform(patch("/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"먼저씀"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NICKNAME_DUPLICATED"));
    }

    @Test
    @DisplayName("프로필 수정은 온보딩 완료 상태를 건드리지 않는다")
    void 프로필수정_온보딩상태_불변() throws Exception {
        User user = userRepository.save(User.builder().nickname("여행자9004").build());
        String accessToken = tokenProvider.issueAccessToken(user.getId(), Role.USER);

        mvc.perform(patch("/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"새닉네임"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getNickname()).isEqualTo("새닉네임");
        assertThat(reloaded.isOnboardingCompleted()).isFalse();
    }
}
