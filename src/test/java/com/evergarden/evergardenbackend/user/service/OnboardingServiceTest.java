package com.evergarden.evergardenbackend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.user.dto.MyProfile;
import com.evergarden.evergardenbackend.user.dto.ProfileUpdateRequest;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** 초기 설정 완료·건너뛰기(ONB-01·02)를 다룬다. */
class OnboardingServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final OnboardingService service = new OnboardingService(userRepository);

    private User newUser() {
        User user = User.builder().nickname("여행자1234").build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    // ── 초기 설정 완료 ────────────────────────────────────────

    @Test
    @DisplayName("닉네임을 보내면 그 값으로 온보딩을 완료한다")
    void 완료_닉네임있음() {
        User user = newUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.saveAndFlush(user)).willReturn(user);

        MyProfile result = service.completeOnboarding(1L, new ProfileUpdateRequest("새닉네임", "http://img"));

        assertThat(result.nickname()).isEqualTo("새닉네임");
        assertThat(result.profileImageUrl()).isEqualTo("http://img");
        assertThat(result.onboardingCompleted()).isTrue();
        assertThat(result.stats()).isNull();
    }

    @Test
    @DisplayName("닉네임을 안 보내면 가입 시점의 자동 생성 닉네임을 그대로 둔다")
    void 완료_닉네임없음_기존값유지() {
        User user = newUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.saveAndFlush(user)).willReturn(user);

        MyProfile result = service.completeOnboarding(1L, new ProfileUpdateRequest(null, "http://img"));

        assertThat(result.nickname()).isEqualTo("여행자1234");
        assertThat(result.profileImageUrl()).isEqualTo("http://img");
    }

    @Test
    @DisplayName("닉네임·프로필이미지 둘 다 없으면 INVALID_REQUEST")
    void 완료_둘다없음() {
        given(userRepository.findById(1L)).willReturn(Optional.of(newUser()));

        assertThatThrownBy(() -> service.completeOnboarding(1L, new ProfileUpdateRequest(null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("이미 온보딩을 마친 사용자면 ONBOARDING_ALREADY_COMPLETED")
    void 완료_이미완료() {
        User user = newUser();
        user.completeOnboarding("기존닉네임", null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.completeOnboarding(1L, new ProfileUpdateRequest("새닉네임", null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ONBOARDING_ALREADY_COMPLETED);
    }

    @Test
    @DisplayName("닉네임이 경합으로 겹치면 NICKNAME_DUPLICATED — 미리 조회하지 않고 저장 시도로 잡는다(ADR-006)")
    void 완료_닉네임경합() {
        User user = newUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.saveAndFlush(user))
                .willThrow(new DataIntegrityViolationException("nickname unique violation"));

        assertThatThrownBy(() -> service.completeOnboarding(1L, new ProfileUpdateRequest("인기닉네임", null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NICKNAME_DUPLICATED);
    }

    @Test
    @DisplayName("없는 유저면 USER_NOT_FOUND")
    void 완료_유저없음() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeOnboarding(99L, new ProfileUpdateRequest("닉네임", null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    // ── 초기 설정 건너뛰기 ────────────────────────────────────

    @Test
    @DisplayName("건너뛰면 온보딩만 완료되고 닉네임은 그대로다")
    void 건너뛰기_성공() {
        User user = newUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        MyProfile result = service.skipOnboarding(1L);

        assertThat(result.onboardingCompleted()).isTrue();
        assertThat(result.nickname()).isEqualTo("여행자1234");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("이미 온보딩을 마친 사용자면 ONBOARDING_ALREADY_COMPLETED")
    void 건너뛰기_이미완료() {
        User user = newUser();
        user.skipOnboarding();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.skipOnboarding(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ONBOARDING_ALREADY_COMPLETED);
    }
}
