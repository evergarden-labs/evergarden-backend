package com.evergarden.evergardenbackend.admin.service;

import com.evergarden.evergardenbackend.admin.dto.AdminUserDetail;
import com.evergarden.evergardenbackend.admin.dto.AdminUserSummary;
import com.evergarden.evergardenbackend.admin.dto.SanctionResponse;
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
import com.evergarden.evergardenbackend.report.entity.Sanction;
import com.evergarden.evergardenbackend.report.entity.SanctionType;
import com.evergarden.evergardenbackend.report.repository.SanctionRepository;
import com.evergarden.evergardenbackend.user.entity.Admin;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.entity.UserStatus;
import com.evergarden.evergardenbackend.user.repository.AdminRepository;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 관리(ADMIN-12·13·04·05·11).
 *
 * <p>경고·차단은 관리자가 재량으로 직접 거는 수동 제재({@code SanctionSource.MANUAL})다.
 * 누적 유효 신고로 시스템이 자동으로 거는 제재(ADMIN-10)는 {@code AdminReportService}가 따로 맡는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final SanctionRepository sanctionRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final NotificationService notificationService;

    public Page<AdminUserSummary> listUsers(String keyword, UserStatus status, Pageable pageable) {
        return userRepository.search(keyword, status, pageable).map(AdminUserSummary::of);
    }

    public AdminUserDetail getUserDetail(Long userId) {
        return buildDetail(findUser(userId));
    }

    /**
     * 관리자가 재량으로 경고한다(ADMIN-04). 신고 누적 자동 경고(ADMIN-10)와는 별개 경로다.
     *
     * <p>이미 차단된 회원이면 상태는 {@code BLOCKED}로 그대로 두고 이력만 남긴다
     * ({@link UserRepository#warnIfNotBlocked}) — 차단은 별도 해제 오퍼레이션(ADMIN-11)으로만
     * 풀려야 하는데, {@code User.warn()}으로 바로 덮어쓰면 그 규칙이 깨진다(ADR-033).
     */
    @Transactional
    public SanctionResponse warnUser(Long adminId, Long userId, String reason) {
        User user = findUser(userId);
        Admin admin = adminRepository.getReferenceById(adminId);
        userRepository.warnIfNotBlocked(userId);
        Sanction sanction = sanctionRepository.save(Sanction.byAdmin(user, SanctionType.WARNING, reason, admin));
        notificationService.notify(user, NotificationType.WARNING, "경고 안내", reason, null, null);
        return SanctionResponse.of(sanction);
    }

    /**
     * 차단한다(ADMIN-05). 발급된 토큰을 폐기해 즉시 로그인이 막히게 한다 —
     * 액세스 토큰은 만료 전까지 여전히 유효하지만, 매 요청 회원 상태를 DB에서
     * 다시 확인하는 {@code JwtAuthenticationFilter}가 곧바로 {@code USER_BLOCKED}로 막는다.
     */
    @Transactional
    public SanctionResponse blockUser(Long adminId, Long userId, String reason) {
        User user = findUser(userId);
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
        }
        Admin admin = adminRepository.getReferenceById(adminId);
        user.block();
        refreshTokenStore.revoke(userId);
        Sanction sanction = sanctionRepository.save(Sanction.byAdmin(user, SanctionType.BLOCK, reason, admin));
        return SanctionResponse.of(sanction);
    }

    /**
     * 차단을 해제한다(ADMIN-11). 차단 상태가 아니면 결과가 이미 같은 곳으로
     * 수렴해 있으니 조용히 넘어간다 — 해제 이력도 남기지 않는다(멱등).
     */
    @Transactional
    public AdminUserDetail unblockUser(Long adminId, Long userId, String reason) {
        User user = findUser(userId);
        if (user.getStatus() == UserStatus.BLOCKED) {
            Admin admin = adminRepository.getReferenceById(adminId);
            user.unblock();
            sanctionRepository.save(Sanction.byAdmin(user, SanctionType.UNBLOCK, reason, admin));
        }
        return buildDetail(user);
    }

    private AdminUserDetail buildDetail(User user) {
        List<String> socialProviders = socialAccountRepository.findByUser_Id(user.getId()).stream()
                .map(sa -> sa.getProvider().name().toLowerCase())
                .toList();
        int postCount = (int) postRepository.countByAuthor_IdAndStatus(user.getId(), PostStatus.ACTIVE);
        int commentCount = (int) commentRepository.countByAuthor_IdAndStatus(user.getId(), CommentStatus.ACTIVE);
        List<SanctionResponse> sanctions = sanctionRepository.findByUser_IdOrderByCreatedAtDesc(user.getId())
                .stream().map(SanctionResponse::of).toList();
        return AdminUserDetail.of(user, socialProviders, postCount, commentCount, sanctions);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
