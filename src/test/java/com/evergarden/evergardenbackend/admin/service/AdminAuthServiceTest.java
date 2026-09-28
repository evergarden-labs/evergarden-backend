package com.evergarden.evergardenbackend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.evergarden.evergardenbackend.admin.dto.AdminSession;
import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.security.JwtTokenProvider;
import com.evergarden.evergardenbackend.global.security.Role;
import com.evergarden.evergardenbackend.user.entity.Admin;
import com.evergarden.evergardenbackend.user.repository.AdminRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** 관리자 로그인(ADMIN-01)을 다룬다. */
class AdminAuthServiceTest {

    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
    private final AdminAuthService service = new AdminAuthService(adminRepository, passwordEncoder, tokenProvider);

    private Admin admin;

    @BeforeEach
    void setUp() {
        admin = Admin.builder().loginId("ops").passwordHash("{bcrypt}hash").name("담당자").build();
        ReflectionTestUtils.setField(admin, "id", 1L);
    }

    @Test
    @DisplayName("아이디·비밀번호가 맞으면 액세스 토큰만 담긴 세션을 돌려준다 — refreshToken은 null")
    void 로그인_성공() {
        given(adminRepository.findByLoginId("ops")).willReturn(Optional.of(admin));
        given(passwordEncoder.matches("pw", "{bcrypt}hash")).willReturn(true);
        given(tokenProvider.issueAccessToken(1L, Role.ADMIN)).willReturn("access-token");

        AdminSession result = service.login("ops", "pw");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isNull();
        assertThat(result.adminName()).isEqualTo("담당자");
    }

    @Test
    @DisplayName("없는 아이디면 ADMIN_CREDENTIALS_INVALID — 존재 여부를 노출하지 않는다")
    void 로그인_없는아이디() {
        given(adminRepository.findByLoginId("nobody")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("nobody", "pw"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ADMIN_CREDENTIALS_INVALID);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 ADMIN_CREDENTIALS_INVALID — 아이디가 맞았다는 걸 알리지 않는다")
    void 로그인_비밀번호틀림() {
        given(adminRepository.findByLoginId("ops")).willReturn(Optional.of(admin));
        given(passwordEncoder.matches(any(), any())).willReturn(false);

        assertThatThrownBy(() -> service.login("ops", "wrong"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ADMIN_CREDENTIALS_INVALID);
    }
}
