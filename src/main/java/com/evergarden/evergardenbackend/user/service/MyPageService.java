package com.evergarden.evergardenbackend.user.service;

import com.evergarden.evergardenbackend.archive.repository.ArchiveRepository;
import com.evergarden.evergardenbackend.community.entity.PostStatus;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.garden.dto.Garden;
import com.evergarden.evergardenbackend.garden.service.GardenService;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.map.repository.RegionVisitRepository;
import com.evergarden.evergardenbackend.timecapsule.entity.TimeCapsuleStatus;
import com.evergarden.evergardenbackend.timecapsule.repository.TimeCapsuleRepository;
import com.evergarden.evergardenbackend.trip.repository.TripRepository;
import com.evergarden.evergardenbackend.user.dto.MyProfile;
import com.evergarden.evergardenbackend.user.dto.MyStats;
import com.evergarden.evergardenbackend.user.dto.ProfileUpdateRequest;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 프로필 조회·수정(MY-01·02). */
@Service
@RequiredArgsConstructor
@Transactional
public class MyPageService {

    private final UserRepository userRepository;
    private final ArchiveRepository archiveRepository;
    private final TripRepository tripRepository;
    private final RegionVisitRepository regionVisitRepository;
    private final GardenService gardenService;
    private final PostRepository postRepository;
    private final TimeCapsuleRepository timeCapsuleRepository;

    /**
     * 내 프로필과 기록 현황을 반환한다(MY-01). 네 갈래(ADR-034)에 걸쳐 여러 도메인의
     * 리포지토리를 직접 주입받아 순차 조회한다 — {@code ReportService}가 다른 도메인의
     * 리포지토리를 직접 참조하는 것과 같은 선례를 따른다.
     */
    @Transactional(readOnly = true)
    public MyProfile getMyProfile(Long userId) {
        User user = findUser(userId);
        MyStats stats = buildStats(userId);
        return new MyProfile(
                user.getId(), user.getNickname(), user.getProfileImageUrl(), user.isOnboardingCompleted(), stats);
    }

    /**
     * 프로필을 수정한다(MY-02). 온보딩 완료 처리는 절대 하지 않는다(ADR-021) —
     * {@code User.updateProfile()}은 그 필드를 아예 건드리지 않는다.
     *
     * <p>닉네임은 사용자가 직접 고르는 값이라 경합이 흔할 수 있어, 미리 조회하지 않고
     * 저장을 시도한 뒤 유니크 제약 위반을 잡는다(ADR-006) — {@code completeOnboarding}과 같은 이유.
     */
    public MyProfile updateMyProfile(Long userId, ProfileUpdateRequest request) {
        if (request.nickname() == null && request.profileImageUrl() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        User user = findUser(userId);
        user.updateProfile(request.nickname(), request.profileImageUrl());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        return MyProfile.withoutStats(
                user.getId(), user.getNickname(), user.getProfileImageUrl(), user.isOnboardingCompleted());
    }

    private MyStats buildStats(Long userId) {
        Garden garden = gardenService.getMyGarden(userId);
        return new MyStats(
                (int) archiveRepository.countByOwner_Id(userId),
                (int) tripRepository.countByOwner_Id(userId),
                regionVisitRepository.aggregateByUser(userId).size(),
                garden.unlockedCount(),
                garden.totalCount(),
                (int) postRepository.countByAuthor_IdAndStatus(userId, PostStatus.ACTIVE),
                (int) postRepository.sumLikeCountByAuthor(userId),
                (int) timeCapsuleRepository.countByOwner_IdAndStatus(userId, TimeCapsuleStatus.SEALED),
                (int) timeCapsuleRepository.countByOwner_IdAndStatus(userId, TimeCapsuleStatus.UNLOCKABLE));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
