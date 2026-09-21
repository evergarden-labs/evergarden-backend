package com.evergarden.evergardenbackend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.evergarden.evergardenbackend.community.entity.PostStatus;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.user.dto.PublicProfile;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 공개 프로필 조회·검색(COMM-20 · ARCH-10)의 노출 규칙을 확인한다. */
class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);

    private final UserService userService = new UserService(userRepository, postRepository);

    private User user(Long id, String nickname) {
        User user = User.builder().nickname(nickname).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    // ── 프로필 조회 ──────────────────────────────────────────

    @Test
    @DisplayName("없는 사용자는 USER_NOT_FOUND")
    void 프로필_없는사용자() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("탈퇴한 사용자는 존재 자체를 알리지 않고 USER_NOT_FOUND")
    void 프로필_탈퇴한사용자() {
        User user = user(5L, "여행자");
        user.withdraw(LocalDateTime.now());
        given(userRepository.findById(5L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.getProfile(5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("차단된 사용자도 USER_NOT_FOUND")
    void 프로필_차단된사용자() {
        User user = user(5L, "여행자");
        user.block();
        given(userRepository.findById(5L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.getProfile(5L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("경고 상태는 여전히 조회된다 — 차단과 다르다")
    void 프로필_경고상태는_조회됨() {
        User user = user(5L, "여행자");
        user.warn();
        given(userRepository.findById(5L)).willReturn(Optional.of(user));
        given(postRepository.countByAuthor_IdAndStatus(5L, PostStatus.ACTIVE)).willReturn(3L);

        PublicProfile result = userService.getProfile(5L);

        assertThat(result.postCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("정상 조회는 공개된 게시물 수를 함께 돌려준다")
    void 프로필_정상() {
        User user = user(5L, "여행자");
        given(userRepository.findById(5L)).willReturn(Optional.of(user));
        given(postRepository.countByAuthor_IdAndStatus(5L, PostStatus.ACTIVE)).willReturn(7L);

        PublicProfile result = userService.getProfile(5L);

        assertThat(result.userId()).isEqualTo(5L);
        assertThat(result.nickname()).isEqualTo("여행자");
        assertThat(result.postCount()).isEqualTo(7);
    }

    // ── 닉네임 검색 ──────────────────────────────────────────

    @Test
    @DisplayName("일치하는 닉네임이 없으면 빈 목록")
    void 검색_없음() {
        given(userRepository.findByNickname("없는사람")).willReturn(Optional.empty());

        assertThat(userService.search("없는사람")).isEmpty();
    }

    @Test
    @DisplayName("탈퇴·차단 회원은 검색 결과에서 빠진다")
    void 검색_탈퇴한사람() {
        User user = user(5L, "탈퇴자");
        user.withdraw(LocalDateTime.now());
        given(userRepository.findByNickname("탈퇴자")).willReturn(Optional.of(user));

        assertThat(userService.search("탈퇴자")).isEmpty();
    }

    @Test
    @DisplayName("정확히 일치하면 0개 아니면 1개를 돌려준다")
    void 검색_정상() {
        User user = user(5L, "여행자9183");
        given(userRepository.findByNickname("여행자9183")).willReturn(Optional.of(user));
        given(postRepository.countByAuthor_IdAndStatus(5L, PostStatus.ACTIVE)).willReturn(0L);

        List<PublicProfile> result = userService.search("여행자9183");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(5L);
    }
}
