package com.evergarden.evergardenbackend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.admin.dto.AdminUserDetail;
import com.evergarden.evergardenbackend.admin.dto.AdminUserSummary;
import com.evergarden.evergardenbackend.admin.dto.SanctionResponse;
import com.evergarden.evergardenbackend.auth.entity.SocialAccount;
import com.evergarden.evergardenbackend.auth.entity.SocialProvider;
import com.evergarden.evergardenbackend.auth.repository.SocialAccountRepository;
import com.evergarden.evergardenbackend.community.entity.CommentStatus;
import com.evergarden.evergardenbackend.community.entity.PostStatus;
import com.evergarden.evergardenbackend.community.repository.CommentRepository;
import com.evergarden.evergardenbackend.community.repository.PostRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.security.RefreshTokenStore;
import com.evergarden.evergardenbackend.notification.entity.NotificationType;
import com.evergarden.evergardenbackend.notification.service.NotificationService;
import com.evergarden.evergardenbackend.report.entity.SanctionType;
import com.evergarden.evergardenbackend.report.repository.SanctionRepository;
import com.evergarden.evergardenbackend.user.entity.Admin;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.entity.UserStatus;
import com.evergarden.evergardenbackend.user.repository.AdminRepository;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/** 회원 관리(ADMIN-12·13·04·05·11)를 다룬다. */
class AdminUserServiceTest {

    private static final Long ADMIN_ID = 9L;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
    private final PostRepository postRepository = mock(PostRepository.class);
    private final CommentRepository commentRepository = mock(CommentRepository.class);
    private final SanctionRepository sanctionRepository = mock(SanctionRepository.class);
    private final RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final AdminUserService service = new AdminUserService(userRepository, adminRepository,
            socialAccountRepository, postRepository, commentRepository, sanctionRepository,
            refreshTokenStore, notificationService);

    private User user;
    private Admin admin;

    @BeforeEach
    void setUp() {
        user = User.builder().nickname("여행자").build();
        ReflectionTestUtils.setField(user, "id", 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        admin = Admin.builder().loginId("ops").passwordHash("hash").name("담당자").build();
        ReflectionTestUtils.setField(admin, "id", ADMIN_ID);
        given(adminRepository.getReferenceById(ADMIN_ID)).willReturn(admin);

        given(socialAccountRepository.findByUser_Id(1L)).willReturn(List.of());
        given(postRepository.countByAuthor_IdAndStatus(1L, PostStatus.ACTIVE)).willReturn(0L);
        given(commentRepository.countByAuthor_IdAndStatus(1L, CommentStatus.ACTIVE)).willReturn(0L);
        given(sanctionRepository.findByUser_IdOrderByCreatedAtDesc(1L)).willReturn(List.of());
        given(sanctionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("목록 조회는 키워드·상태로 리포지토리에 위임하고 요약으로 매핑한다")
    void 목록_조회() {
        Page<User> page = new PageImpl<>(List.of(user), PageRequest.of(0, 20), 1);
        given(userRepository.search("여행", UserStatus.ACTIVE, PageRequest.of(0, 20))).willReturn(page);

        Page<AdminUserSummary> result = service.listUsers("여행", UserStatus.ACTIVE, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).userId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("상세 조회는 소셜 연결·게시물/댓글 수·제재 이력을 함께 담는다")
    void 상세_조회() {
        SocialAccount google = mock(SocialAccount.class);
        given(google.getProvider()).willReturn(SocialProvider.GOOGLE);
        given(socialAccountRepository.findByUser_Id(1L)).willReturn(List.of(google));
        given(postRepository.countByAuthor_IdAndStatus(1L, PostStatus.ACTIVE)).willReturn(3L);
        given(commentRepository.countByAuthor_IdAndStatus(1L, CommentStatus.ACTIVE)).willReturn(5L);

        AdminUserDetail result = service.getUserDetail(1L);

        assertThat(result.socialProviders()).containsExactly("google");
        assertThat(result.postCount()).isEqualTo(3);
        assertThat(result.commentCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("없는 회원이면 USER_NOT_FOUND")
    void 상세_없는회원() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserDetail(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("경고는 상태를 WARNED로 바꾸는 원자적 쿼리를 부르고 사유를 담은 제재를 남기고 알림을 보낸다")
    void 경고() {
        SanctionResponse result = service.warnUser(ADMIN_ID, 1L, "정책 위반");

        verify(userRepository).warnIfNotBlocked(1L);
        assertThat(result.type()).isEqualTo(SanctionType.WARNING);
        assertThat(result.reason()).isEqualTo("정책 위반");
        verify(notificationService).notify(
                eq(user), eq(NotificationType.WARNING), any(), eq("정책 위반"), eq(null), eq(null));
    }

    @Test
    @DisplayName("차단은 상태를 BLOCKED로 바꾸고 리프레시 토큰을 폐기한다")
    void 차단() {
        SanctionResponse result = service.blockUser(ADMIN_ID, 1L, "약관 위반");

        assertThat(user.getStatus()).isEqualTo(UserStatus.BLOCKED);
        assertThat(result.type()).isEqualTo(SanctionType.BLOCK);
        verify(refreshTokenStore).revoke(1L);
    }

    @Test
    @DisplayName("이미 차단된 회원을 또 차단하면 DUPLICATE_REQUEST — 토큰도 다시 폐기하지 않는다")
    void 차단_중복() {
        user.block();

        assertThatThrownBy(() -> service.blockUser(ADMIN_ID, 1L, "약관 위반"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_REQUEST);
        verify(refreshTokenStore, never()).revoke(any());
    }

    @Test
    @DisplayName("차단된 회원을 해제하면 ACTIVE로 돌아오고 UNBLOCK 이력이 남는다")
    void 차단해제() {
        user.block();

        AdminUserDetail result = service.unblockUser(ADMIN_ID, 1L, "이의 제기 수용");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
        verify(sanctionRepository).save(argThat(s -> s.getType() == SanctionType.UNBLOCK));
    }

    @Test
    @DisplayName("차단되지 않은 회원을 해제해도 200 — 멱등, 이력도 남기지 않는다")
    void 차단해제_멱등() {
        AdminUserDetail result = service.unblockUser(ADMIN_ID, 1L, "실수로 눌렀지만 이미 정상 회원");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
        verify(sanctionRepository, never()).save(any());
    }
}
