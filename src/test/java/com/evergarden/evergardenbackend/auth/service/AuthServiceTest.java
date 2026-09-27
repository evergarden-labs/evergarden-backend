package com.evergarden.evergardenbackend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.evergarden.evergardenbackend.auth.client.SocialAuthClient;
import com.evergarden.evergardenbackend.auth.dto.AuthResult;
import com.evergarden.evergardenbackend.auth.dto.TokenPair;
import com.evergarden.evergardenbackend.auth.entity.SocialAccount;
import com.evergarden.evergardenbackend.auth.entity.SocialProvider;
import com.evergarden.evergardenbackend.auth.repository.SocialAccountRepository;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.security.IssuedRefreshToken;
import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.RefreshTokenPrincipal;
import com.evergarden.evergardenbackend.global.security.RefreshTokenStore;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.report.entity.Sanction;
import com.evergarden.evergardenbackend.report.entity.SanctionType;
import com.evergarden.evergardenbackend.report.repository.SanctionRepository;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import com.evergarden.evergardenbackend.user.service.NicknameGenerator;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** AUTH-01~05를 다룬다(AUTH-07 {@code restoreAccount}는 명세 확인 후 별도로 붙인다). */
class AuthServiceTest {

    private static final int RESTORE_GRACE_DAYS = 30;

    private final SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SanctionRepository sanctionRepository = mock(SanctionRepository.class);
    private final JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
    private final RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
    private final NicknameGenerator nicknameGenerator = mock(NicknameGenerator.class);
    private final SocialAuthClient googleClient = mock(SocialAuthClient.class);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        given(googleClient.provider()).willReturn(SocialProvider.GOOGLE);
        authService = new AuthService(
                socialAccountRepository, userRepository, sanctionRepository, tokenProvider,
                refreshTokenStore, nicknameGenerator, List.of(googleClient), RESTORE_GRACE_DAYS);
    }

    private User existingUser() {
        User user = User.builder().nickname("기존유저").build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    // ── 소셜 로그인·가입 ─────────────────────────────────────

    @Test
    @DisplayName("처음 보는 소셜 계정이면 가입하고 isNewUser=true를 돌려준다")
    void 신규_가입() {
        given(googleClient.verify("token")).willReturn("provider-uid-1");
        given(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "provider-uid-1"))
                .willReturn(Optional.empty());
        given(nicknameGenerator.generate()).willReturn("여행자1234");
        given(tokenProvider.issueAccessToken(any(), eq(Role.USER))).willReturn("access");
        given(tokenProvider.issueRefreshToken(any(), eq(Role.USER)))
                .willReturn(new IssuedRefreshToken("refresh", "jti-1"));

        AuthResult result = authService.loginWithSocial("google", "token");

        assertThat(result.isNewUser()).isTrue();
        assertThat(result.onboardingCompleted()).isFalse();
        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
        verify(socialAccountRepository).saveAndFlush(any(SocialAccount.class));
        verify(refreshTokenStore).save(any(), eq("jti-1"));
    }

    @Test
    @DisplayName("이미 연결된 소셜 계정이면 기존 회원으로 로그인한다")
    void 기존_회원_로그인() {
        User user = existingUser();
        SocialAccount account = SocialAccount.builder()
                .user(user).provider(SocialProvider.GOOGLE).providerUserId("provider-uid-1").build();
        given(googleClient.verify("token")).willReturn("provider-uid-1");
        given(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "provider-uid-1"))
                .willReturn(Optional.of(account));
        given(tokenProvider.issueAccessToken(1L, Role.USER)).willReturn("access");
        given(tokenProvider.issueRefreshToken(1L, Role.USER))
                .willReturn(new IssuedRefreshToken("refresh", "jti-1"));

        AuthResult result = authService.loginWithSocial("google", "token");

        assertThat(result.isNewUser()).isFalse();
        verify(socialAccountRepository, never()).saveAndFlush(any());
        verify(refreshTokenStore).save(1L, "jti-1");
    }

    @Test
    @DisplayName("차단된 회원이 로그인하면 USER_BLOCKED — 사유·차단시점을 담는다(AUTH-06)")
    void 차단된_회원_로그인_거절() {
        User user = existingUser();
        user.block();
        SocialAccount account = SocialAccount.builder()
                .user(user).provider(SocialProvider.GOOGLE).providerUserId("uid").build();
        given(googleClient.verify("token")).willReturn("uid");
        given(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "uid"))
                .willReturn(Optional.of(account));

        Sanction sanction = Sanction.byAdmin(user, SanctionType.BLOCK, "정책 위반", null);
        LocalDateTime blockedAt = LocalDateTime.now();
        ReflectionTestUtils.setField(sanction, "createdAt", blockedAt);
        given(sanctionRepository.findTopByUser_IdAndTypeOrderByCreatedAtDesc(1L, SanctionType.BLOCK))
                .willReturn(Optional.of(sanction));

        assertThatThrownBy(() -> authService.loginWithSocial("google", "token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_BLOCKED)
                .satisfies(e -> {
                    var details = ((BusinessException) e).getDetails();
                    assertThat(details.get("reason")).isEqualTo("정책 위반");
                    assertThat(details.get("blockedAt")).isEqualTo(blockedAt.toString());
                });
    }

    @Test
    @DisplayName("탈퇴 유예 안인 회원이 로그인하면 USER_WITHDRAWN + restorableUntil")
    void 탈퇴_유예중_로그인_거절() {
        User user = existingUser();
        user.withdraw(LocalDateTime.now().minusDays(5));
        SocialAccount account = SocialAccount.builder()
                .user(user).provider(SocialProvider.GOOGLE).providerUserId("uid").build();
        given(googleClient.verify("token")).willReturn("uid");
        given(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "uid"))
                .willReturn(Optional.of(account));

        assertThatThrownBy(() -> authService.loginWithSocial("google", "token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_WITHDRAWN)
                .satisfies(e -> assertThat(((BusinessException) e).getDetails())
                        .containsKey("restorableUntil"));
    }

    @Test
    @DisplayName("탈퇴 유예가 지난 회원이 로그인하면 USER_WITHDRAWN — 복구 기한 안내는 없다")
    void 탈퇴_유예만료_로그인_거절() {
        User user = existingUser();
        user.withdraw(LocalDateTime.now().minusDays(40));
        SocialAccount account = SocialAccount.builder()
                .user(user).provider(SocialProvider.GOOGLE).providerUserId("uid").build();
        given(googleClient.verify("token")).willReturn("uid");
        given(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "uid"))
                .willReturn(Optional.of(account));

        assertThatThrownBy(() -> authService.loginWithSocial("google", "token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_WITHDRAWN)
                .satisfies(e -> assertThat(((BusinessException) e).getDetails()).isNull());
    }

    @Test
    @DisplayName("동시 가입 경합 — 유니크 제약에 걸리면 SOCIAL_ACCOUNT_ALREADY_LINKED(ADR-006)")
    void 동시_가입_경합() {
        given(googleClient.verify("token")).willReturn("uid");
        given(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "uid"))
                .willReturn(Optional.empty());
        given(nicknameGenerator.generate()).willReturn("여행자9999");
        given(socialAccountRepository.saveAndFlush(any(SocialAccount.class)))
                .willThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> authService.loginWithSocial("google", "token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
    }

    @Test
    @DisplayName("모르는 제공자 값이면 INVALID_REQUEST")
    void 모르는_제공자() {
        assertThatThrownBy(() -> authService.loginWithSocial("facebook", "token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    // ── 토큰 재발급 ──────────────────────────────────────────

    @Test
    @DisplayName("유효한 리프레시 토큰이면 rotation으로 새 토큰 쌍을 돌려준다")
    void 재발급_성공() {
        given(tokenProvider.parseRefreshToken("old-token"))
                .willReturn(new RefreshTokenPrincipal(1L, Role.USER, "old-jti"));
        given(refreshTokenStore.isValid(1L, "old-jti")).willReturn(true);
        given(tokenProvider.issueAccessToken(1L, Role.USER)).willReturn("new-access");
        given(tokenProvider.issueRefreshToken(1L, Role.USER))
                .willReturn(new IssuedRefreshToken("new-refresh", "new-jti"));

        TokenPair result = authService.refreshToken("old-token");

        assertThat(result).isEqualTo(new TokenPair("new-access", "new-refresh"));
        verify(refreshTokenStore).save(1L, "new-jti");
    }

    @Test
    @DisplayName("Redis에 없는(이미 회전되었거나 폐기된) jti면 REFRESH_TOKEN_EXPIRED — 새 토큰을 발급하지 않는다")
    void 재발급_실패_무효한_jti() {
        given(tokenProvider.parseRefreshToken("old-token"))
                .willReturn(new RefreshTokenPrincipal(1L, Role.USER, "old-jti"));
        given(refreshTokenStore.isValid(1L, "old-jti")).willReturn(false);

        assertThatThrownBy(() -> authService.refreshToken("old-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_EXPIRED);
        verify(tokenProvider, never()).issueAccessToken(anyLong(), any());
    }

    // ── 로그아웃·탈퇴 ────────────────────────────────────────

    @Test
    @DisplayName("로그아웃은 리프레시 토큰을 폐기한다")
    void 로그아웃() {
        authService.logout(1L);

        verify(refreshTokenStore).revoke(1L);
    }

    @Test
    @DisplayName("탈퇴하면 상태가 WITHDRAWN이 되고 리프레시 토큰이 폐기된다")
    void 탈퇴() {
        User user = existingUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        authService.withdraw(1L);

        assertThat(user.getStatus().name()).isEqualTo("WITHDRAWN");
        assertThat(user.getWithdrawnAt()).isNotNull();
        verify(refreshTokenStore).revoke(1L);
    }

    @Test
    @DisplayName("없는 유저를 탈퇴시키면 USER_NOT_FOUND")
    void 탈퇴_대상_없음() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.withdraw(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }
}
