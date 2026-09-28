package com.evergarden.evergardenbackend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.community.entity.PostStatus;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.garden.dto.Garden;
import com.evergarden.evergardenbackend.garden.service.GardenService;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.map.repository.RegionVisitAggregate;
import com.evergarden.evergardenbackend.map.repository.RegionVisitRepository;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsuleStatus;
import com.evergarden.evergardenbackend.timecapsule.repository.TimeCapsuleRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import com.evergarden.evergardenbackend.user.dto.MyProfile;
import com.evergarden.evergardenbackend.user.dto.MyStats;
import com.evergarden.evergardenbackend.user.dto.ProfileUpdateRequest;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** 내 프로필 조회·수정(MY-01·02)을 다룬다. */
class MyPageServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ArchiveRepository archiveRepository = mock(ArchiveRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final RegionVisitRepository regionVisitRepository = mock(RegionVisitRepository.class);
    private final GardenService gardenService = mock(GardenService.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final TimeCapsuleRepository timeCapsuleRepository = mock(TimeCapsuleRepository.class);

    private final MyPageService service = new MyPageService(
            userRepository, archiveRepository, tripRepository, regionVisitRepository,
            gardenService, postRepository, timeCapsuleRepository);

    private User user() {
        User user = User.builder().nickname("여행자1234").build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    // ── 프로필 조회 ──────────────────────────────────────────

    @Test
    @DisplayName("아홉 개 지표를 각 도메인에서 모아 정확히 조립한다")
    void 조회_지표조립() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user()));
        given(archiveRepository.countByOwner_Id(1L)).willReturn(3L);
        given(tripRepository.countByOwner_Id(1L)).willReturn(2L);
        RegionVisitAggregate seoul = mock(RegionVisitAggregate.class);
        RegionVisitAggregate busan = mock(RegionVisitAggregate.class);
        given(regionVisitRepository.aggregateByUser(1L)).willReturn(List.of(seoul, busan));
        given(gardenService.getMyGarden(1L)).willReturn(new Garden(List.of(), 5, 40));
        given(postRepository.countByAuthor_IdAndStatus(1L, PostStatus.ACTIVE)).willReturn(7L);
        given(postRepository.sumLikeCountByAuthor(1L)).willReturn(120L);
        given(timeCapsuleRepository.countByOwner_IdAndStatus(1L, TimeCapsuleStatus.SEALED)).willReturn(4L);
        given(timeCapsuleRepository.countByOwner_IdAndStatus(1L, TimeCapsuleStatus.UNLOCKABLE)).willReturn(1L);

        MyProfile result = service.getMyProfile(1L);

        MyStats stats = result.stats();
        assertThat(stats.archiveCount()).isEqualTo(3);
        assertThat(stats.tripCount()).isEqualTo(2);
        assertThat(stats.visitedRegionCount()).isEqualTo(2);
        assertThat(stats.gardenUnlockedCount()).isEqualTo(5);
        assertThat(stats.gardenTotalCount()).isEqualTo(40);
        assertThat(stats.postCount()).isEqualTo(7);
        assertThat(stats.receivedLikeCount()).isEqualTo(120);
        assertThat(stats.sealedCapsuleCount()).isEqualTo(4);
        assertThat(stats.unlockableCapsuleCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("아무 기록도 없는 신규 사용자는 아홉 개 다 0이다 — receivedLikeCount의 COALESCE 포함")
    void 조회_신규사용자_전부0() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user()));
        given(archiveRepository.countByOwner_Id(1L)).willReturn(0L);
        given(tripRepository.countByOwner_Id(1L)).willReturn(0L);
        given(regionVisitRepository.aggregateByUser(1L)).willReturn(List.of());
        given(gardenService.getMyGarden(1L)).willReturn(new Garden(List.of(), 0, 40));
        given(postRepository.countByAuthor_IdAndStatus(1L, PostStatus.ACTIVE)).willReturn(0L);
        given(postRepository.sumLikeCountByAuthor(1L)).willReturn(0L);
        given(timeCapsuleRepository.countByOwner_IdAndStatus(1L, TimeCapsuleStatus.SEALED)).willReturn(0L);
        given(timeCapsuleRepository.countByOwner_IdAndStatus(1L, TimeCapsuleStatus.UNLOCKABLE)).willReturn(0L);

        MyStats stats = service.getMyProfile(1L).stats();

        assertThat(stats.archiveCount()).isZero();
        assertThat(stats.tripCount()).isZero();
        assertThat(stats.visitedRegionCount()).isZero();
        assertThat(stats.gardenUnlockedCount()).isZero();
        assertThat(stats.postCount()).isZero();
        assertThat(stats.receivedLikeCount()).isZero();
        assertThat(stats.sealedCapsuleCount()).isZero();
        assertThat(stats.unlockableCapsuleCount()).isZero();
    }

    @Test
    @DisplayName("없는 유저면 USER_NOT_FOUND")
    void 조회_유저없음() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyProfile(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    // ── 프로필 수정 ──────────────────────────────────────────

    @Test
    @DisplayName("닉네임·프로필이미지를 바꾸고 stats 없이 돌려준다 — 온보딩 완료 처리는 안 한다")
    void 수정_성공() {
        User user = user();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.saveAndFlush(user)).willReturn(user);

        MyProfile result = service.updateMyProfile(1L, new ProfileUpdateRequest("새닉네임", "http://img"));

        assertThat(result.nickname()).isEqualTo("새닉네임");
        assertThat(result.profileImageUrl()).isEqualTo("http://img");
        assertThat(result.stats()).isNull();
        assertThat(user.isOnboardingCompleted()).isFalse();
    }

    @Test
    @DisplayName("닉네임·프로필이미지 둘 다 없으면 INVALID_REQUEST")
    void 수정_둘다없음() {
        assertThatThrownBy(() -> service.updateMyProfile(1L, new ProfileUpdateRequest(null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("닉네임이 경합으로 겹치면 NICKNAME_DUPLICATED(ADR-006)")
    void 수정_닉네임경합() {
        User user = user();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.saveAndFlush(user))
                .willThrow(new DataIntegrityViolationException("nickname unique violation"));

        assertThatThrownBy(() -> service.updateMyProfile(1L, new ProfileUpdateRequest("인기닉네임", null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NICKNAME_DUPLICATED);
    }

    @Test
    @DisplayName("없는 유저면 USER_NOT_FOUND")
    void 수정_유저없음() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMyProfile(99L, new ProfileUpdateRequest("닉네임", null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }
}
