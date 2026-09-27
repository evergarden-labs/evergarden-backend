package com.evergarden.evergardenbackend.user.service;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.user.dto.MyProfile;
import com.evergarden.evergardenbackend.user.dto.ProfileUpdateRequest;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 초기 설정 완료·건너뛰기(ONB-01·02). */
@Service
@RequiredArgsConstructor
@Transactional
public class OnboardingService {

    private final UserRepository userRepository;

    /**
     * 초기 설정을 완료한다(ONB-01). 한 번만 할 수 있다(ADR-021) — 두 번째 호출은 거절한다.
     *
     * <p>닉네임을 안 보내면(명세가 필수로 두지 않는다) 가입 시점에 자동 생성된 값을 그대로 둔다.
     * 사용자가 직접 고르는 값이라 자동생성 닉네임보다 경합이 훨씬 흔할 수 있어, 미리 조회하지
     * 않고 저장을 시도한 뒤 유니크 제약 위반을 잡는다(ADR-006).
     */
    public MyProfile completeOnboarding(Long userId, ProfileUpdateRequest request) {
        User user = findUser(userId);
        if (user.isOnboardingCompleted()) {
            throw new BusinessException(ErrorCode.ONBOARDING_ALREADY_COMPLETED);
        }
        if (request.nickname() == null && request.profileImageUrl() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        String nickname = request.nickname() != null ? request.nickname() : user.getNickname();
        user.completeOnboarding(nickname, request.profileImageUrl());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        return toProfile(user);
    }

    /**
     * 초기 설정을 건너뛴다(ONB-02). 가입 시점에 이미 만들어 둔 기본 닉네임을 그대로 쓴다
     * (ADR-050, {@code AuthService.registerNewUser}) — 여기서 새로 만들지 않는다.
     */
    public MyProfile skipOnboarding(Long userId) {
        User user = findUser(userId);
        if (user.isOnboardingCompleted()) {
            throw new BusinessException(ErrorCode.ONBOARDING_ALREADY_COMPLETED);
        }
        user.skipOnboarding();
        return toProfile(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private MyProfile toProfile(User user) {
        return MyProfile.withoutStats(
                user.getId(), user.getNickname(), user.getProfileImageUrl(), user.isOnboardingCompleted());
    }
}
