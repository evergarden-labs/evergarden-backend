package com.evergarden.evergardenbackend.admin.service;

import com.evergarden.evergardenbackend.admin.dto.AdminSession;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.user.entity.Admin;
import com.evergarden.evergardenbackend.user.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 로그인/로그아웃(ADMIN-01·02).
 *
 * <p>아이디·비밀번호 중 무엇이 틀렸는지 구분해 알리지 않는다 — 둘 다
 * {@code ADMIN_CREDENTIALS_INVALID}로 통일한다(계정 존재 여부를 노출하지 않기 위해서다).
 *
 * <p>리프레시 토큰을 발급하지 않는다({@link AdminSession#of}) — 로그아웃도 그래서
 * 토큰 폐기 같은 부수 작업이 없고, 인증 확인({@code ADMIN_ONLY})은 이미
 * {@code SecurityConfig}가 {@code /admin/**}에 걸어둔 규칙으로 충분하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AdminSession login(String loginId, String password) {
        Admin admin = adminRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_CREDENTIALS_INVALID));
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new BusinessException(ErrorCode.ADMIN_CREDENTIALS_INVALID);
        }
        String accessToken = tokenProvider.issueAccessToken(admin.getId(), Role.ADMIN);
        return AdminSession.of(accessToken, admin.getName());
    }
}
