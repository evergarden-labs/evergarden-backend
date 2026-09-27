package com.evergarden.evergardenbackend.auth.service;

import com.evergarden.evergardenbackend.archive.entity.ArchiveCollaborator;
import com.evergarden.evergardenbackend.archive.entity.CollaborationStatus;
import com.evergarden.evergardenbackend.archive.entity.CollaboratorStatus;
import com.evergarden.evergardenbackend.archive.repository.ArchiveCollaboratorRepository;
import com.evergarden.evergardenbackend.archive.service.ArchiveCollaborationService;
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
import com.evergarden.evergardenbackend.user.entity.UserStatus;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import com.evergarden.evergardenbackend.user.service.NicknameGenerator;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증(AUTH-01~07 중 소셜 로그인·재발급·로그아웃·탈퇴). {@code restoreAccount}는 별도로 다룬다. */
@Service
@Transactional
public class AuthService {

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final SanctionRepository sanctionRepository;
    private final ArchiveCollaboratorRepository archiveCollaboratorRepository;
    private final ArchiveCollaborationService archiveCollaborationService;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final NicknameGenerator nicknameGenerator;
    private final List<SocialAuthClient> socialAuthClients;
    private final int restoreGraceDays;

    public AuthService(
            SocialAccountRepository socialAccountRepository,
            UserRepository userRepository,
            SanctionRepository sanctionRepository,
            ArchiveCollaboratorRepository archiveCollaboratorRepository,
            ArchiveCollaborationService archiveCollaborationService,
            JwtTokenProvider tokenProvider,
            RefreshTokenStore refreshTokenStore,
            NicknameGenerator nicknameGenerator,
            List<SocialAuthClient> socialAuthClients,
            @Value("${policy.withdrawal.restore-grace-days}") int restoreGraceDays) {
        this.socialAccountRepository = socialAccountRepository;
        this.userRepository = userRepository;
        this.sanctionRepository = sanctionRepository;
        this.archiveCollaboratorRepository = archiveCollaboratorRepository;
        this.archiveCollaborationService = archiveCollaborationService;
        this.tokenProvider = tokenProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.nicknameGenerator = nicknameGenerator;
        this.socialAuthClients = socialAuthClients;
        this.restoreGraceDays = restoreGraceDays;
    }

    /** 소셜 로그인·회원가입(AUTH-01·02). 차단·탈퇴 회원은 여기서 막는다(AUTH-06). */
    public AuthResult loginWithSocial(String providerRaw, String socialAccessToken) {
        SocialProvider provider = SocialProvider.from(providerRaw);
        String providerUserId = clientFor(provider).verify(socialAccessToken);

        var existing = socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId);
        boolean isNewUser = existing.isEmpty();
        User user;
        if (isNewUser) {
            user = registerNewUser(provider, providerUserId);
        } else {
            user = existing.get().getUser();
            checkLoginable(user);
        }
        return issueAuthResult(user, isNewUser);
    }

    /** 액세스 토큰 재발급(AUTH-05). 리프레시 토큰도 함께 회전한다(ADR-055). */
    public TokenPair refreshToken(String refreshToken) {
        RefreshTokenPrincipal parsed = tokenProvider.parseRefreshToken(refreshToken);
        if (!refreshTokenStore.isValid(parsed.userId(), parsed.jti())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        String accessToken = tokenProvider.issueAccessToken(parsed.userId(), parsed.role());
        IssuedRefreshToken newRefresh = tokenProvider.issueRefreshToken(parsed.userId(), parsed.role());
        refreshTokenStore.save(parsed.userId(), newRefresh.jti());
        return new TokenPair(accessToken, newRefresh.token());
    }

    /** 로그아웃(AUTH-03). 지금 유효한 리프레시 토큰을 폐기한다. */
    public void logout(Long userId) {
        refreshTokenStore.revoke(userId);
    }

    /**
     * 회원 탈퇴(AUTH-04). 상태 전환·토큰 폐기·공동 편집 정리까지 한다(ADR-054).
     * 게시물·댓글 등 장기 데이터 처리 정책은 범위 밖이다 — 기획·법무 결정 필요.
     */
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.withdraw(LocalDateTime.now());
        refreshTokenStore.revoke(userId);
        endArchiveParticipation(userId);
    }

    /**
     * 탈퇴 시 공동 편집을 정리한다(ADR-054). 소유한 아카이브 중 공동 편집이 열려 있으면
     * 종료하고(참여자에게 실시간 알림이 간다 — {@link ArchiveCollaborationService#close}가
     * 이미 발행하는 이벤트), 참여 중이던 아카이브에서는 나간 것으로 처리한다.
     *
     * <p>초대만 받고 아직 수락하지 않은 것(INVITED)은 ADR-054에 명시가 없어 손대지 않는다.
     */
    private void endArchiveParticipation(Long userId) {
        List<ArchiveCollaborator> joined =
                archiveCollaboratorRepository.findByUser_IdAndStatus(userId, CollaboratorStatus.JOINED);
        for (ArchiveCollaborator collaborator : joined) {
            Long archiveId = collaborator.getArchive().getId();
            if (collaborator.isOwner()) {
                if (collaborator.getArchive().getCollaborationStatus() == CollaborationStatus.OPEN) {
                    archiveCollaborationService.close(userId, archiveId);
                }
            } else {
                archiveCollaborationService.leave(userId, archiveId);
            }
        }
    }

    /**
     * {@code provider()}를 매 호출마다 다시 확인한다 — 생성자에서 미리 {@code Map}으로
     * 굳혀두면, 그 시점에 아직 스텁되지 않은 목(mock) 빈이 {@code provider()}로 {@code null}을
     * 돌려줘 컨텍스트 기동 자체가 실패한다(테스트에서 {@code @MockitoBean}으로 클라이언트를
     * 갈아 끼울 때 실제로 겪은 문제).
     */
    private SocialAuthClient clientFor(SocialProvider provider) {
        return socialAuthClients.stream()
                .filter(client -> client.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST));
    }

    /**
     * 신규 회원을 만든다. {@code User} 저장 직후 {@code SocialAccount} 유니크 제약
     * ({@code provider}, {@code providerUserId})에서 동시 가입 경합에 걸리면(ADR-006),
     * 여기서 잡은 예외가 {@link BusinessException}으로 바뀌어 트랜잭션 밖까지 전파되고
     * 스프링이 트랜잭션 전체를 롤백한다 — 방금 만든 {@code User}도 함께 사라져 고아 행이
     * 남지 않는다.
     */
    private User registerNewUser(SocialProvider provider, String providerUserId) {
        User user = User.builder().nickname(nicknameGenerator.generate()).build();
        userRepository.save(user);

        SocialAccount account = SocialAccount.builder()
                .user(user).provider(provider).providerUserId(providerUserId).build();
        try {
            socialAccountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
        }
        return user;
    }

    private void checkLoginable(User user) {
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw blockedException(user);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw withdrawnException(user);
        }
    }

    /** 차단 사유·시점을 안내한다(AUTH-06). 제재 이력이 없으면(이론상 있을 수 없음) 값 없이 내려간다. */
    private BusinessException blockedException(User user) {
        Sanction latestBlock = sanctionRepository
                .findTopByUser_IdAndTypeOrderByCreatedAtDesc(user.getId(), SanctionType.BLOCK)
                .orElse(null);
        Map<String, Object> details = new HashMap<>();
        details.put("reason", latestBlock != null ? latestBlock.getReason() : null);
        details.put("blockedAt", latestBlock != null ? latestBlock.getCreatedAt().toString() : null);
        return new BusinessException(ErrorCode.USER_BLOCKED, details);
    }

    /** {@code JwtAuthenticationFilter.withdrawn()}과 같은 계산이다(ADR-054) — 로그인 시점 전용이라 따로 둔다. */
    private BusinessException withdrawnException(User user) {
        LocalDateTime withdrawnAt = user.getWithdrawnAt();
        if (withdrawnAt == null) {
            return new BusinessException(ErrorCode.USER_WITHDRAWN);
        }
        LocalDateTime restorableUntil = withdrawnAt.plusDays(restoreGraceDays);
        if (LocalDateTime.now().isAfter(restorableUntil)) {
            return new BusinessException(ErrorCode.USER_WITHDRAWN);
        }
        return new BusinessException(ErrorCode.USER_WITHDRAWN,
                Map.of("restorableUntil", restorableUntil.toString()));
    }

    private AuthResult issueAuthResult(User user, boolean isNewUser) {
        TokenPair tokens = issueTokens(user);
        return new AuthResult(tokens.accessToken(), tokens.refreshToken(), isNewUser, user.isOnboardingCompleted());
    }

    private TokenPair issueTokens(User user) {
        String accessToken = tokenProvider.issueAccessToken(user.getId(), Role.USER);
        IssuedRefreshToken refresh = tokenProvider.issueRefreshToken(user.getId(), Role.USER);
        refreshTokenStore.save(user.getId(), refresh.jti());
        return new TokenPair(accessToken, refresh.token());
    }
}
