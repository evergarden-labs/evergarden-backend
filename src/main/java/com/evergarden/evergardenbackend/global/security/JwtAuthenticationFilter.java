package com.evergarden.evergardenbackend.global.security;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.user.entity.User;
import com.evergarden.evergardenbackend.user.entity.UserStatus;
import com.evergarden.evergardenbackend.user.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer <token>}을 읽어 {@link AuthPrincipal}을 세운다.
 *
 * <p><b>여기서 응답을 쓰지 않는다.</b> 문제가 있어도 인증만 세우지 않고 요청을 흘려보낸 뒤,
 * 사유를 요청 속성에 적어둔다. 판단은 뒤쪽 인가 단계가 한다 —
 *
 * <ul>
 *   <li>인증이 필요 없는 경로({@code /auth/restore} 등)면 <b>그대로 통과</b>한다.
 *       탈퇴한 회원이 복구를 부를 수 있어야 하므로 이 동작이 필요하다</li>
 *   <li>인증이 필요한 경로면 {@link JwtAuthenticationEntryPoint}가 적어둔 사유로 응답한다</li>
 * </ul>
 *
 * <p>{@code dev-auth.enabled=true}(dev 프로필 전용)면 토큰이 없는 요청을 {@link DevUserSeeder}가
 * 만들어 둔 고정 사용자로 통과시킨다. 소셜 로그인이 없어도 도메인 API를 바로 호출해볼 수 있게 하기 위해서다.
 *
 * <p>토큰이 유효해도 회원 상태를 <b>매 요청 DB에서 확인</b>한다. 차단·탈퇴는 토큰 발급 뒤에
 * 일어나므로 토큰만 봐서는 알 수 없다. 명세의 생략 규칙상 {@code USER_BLOCKED}·
 * {@code USER_WITHDRAWN}은 인증이 필요한 <b>모든</b> 오퍼레이션에 걸리는 전역 응답이라,
 * 서비스마다 검사하면 언젠가 빠뜨린다.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 인증 실패 사유. {@link JwtAuthenticationEntryPoint}가 읽는다. */
    static final String AUTH_ERROR = JwtAuthenticationFilter.class.getName() + ".AUTH_ERROR";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final DevUserProvider devUserProvider;
    private final int restoreGraceDays;
    private final boolean devAuthEnabled;

    public JwtAuthenticationFilter(
            JwtTokenProvider tokenProvider,
            UserRepository userRepository,
            DevUserProvider devUserProvider,
            @Value("${policy.withdrawal.restore-grace-days}") int restoreGraceDays,
            @Value("${dev-auth.enabled:false}") boolean devAuthEnabled) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.devUserProvider = devUserProvider;
        this.restoreGraceDays = restoreGraceDays;
        this.devAuthEnabled = devAuthEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            authenticate(request, token);
        } else if (devAuthEnabled) {
            // 토큰을 아예 안 들고 온 요청만 해당한다. 실제 토큰이 있으면 정상 검증한다 —
            // 소셜 로그인이 생긴 뒤에도 이 플래그를 켜 둔 채로 실제 토큰을 테스트할 수 있다
            authenticateAsDevUser(request);
        }
        chain.doFilter(request, response);
    }

    /** {@link DevUserSeeder}가 만들어 둔 고정 사용자로 인증을 세운다. */
    private void authenticateAsDevUser(HttpServletRequest request) {
        Long devUserId = devUserProvider.userId();
        if (devUserId == null) {
            // 시더가 아직 안 돌았거나 실패한 상태. 평소처럼 미인증으로 흘려보낸다
            return;
        }
        BusinessException denied = checkUserStatus(devUserId);
        if (denied != null) {
            reject(request, denied);
            return;
        }
        AuthPrincipal principal = new AuthPrincipal(devUserId, Role.USER);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.authorities()));
    }

    private void authenticate(HttpServletRequest request, String token) {
        AuthPrincipal principal;
        try {
            principal = tokenProvider.parseAccessToken(token);
        } catch (ExpiredJwtException e) {
            // 재발급하면 되는 상황. 클라이언트가 이 코드를 보고 /auth/token/refresh를 부른다
            reject(request, new BusinessException(ErrorCode.TOKEN_EXPIRED));
            return;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("token rejected: {}", e.getMessage());
            reject(request, new BusinessException(ErrorCode.TOKEN_INVALID));
            return;
        }

        if (principal.role() == Role.USER) {
            BusinessException denied = checkUserStatus(principal.userId());
            if (denied != null) {
                reject(request, denied);
                return;
            }
        }

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.authorities()));
    }

    /** 이용할 수 없는 상태면 사유를, 정상이면 {@code null}을 돌려준다. */
    private BusinessException checkUserStatus(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            // 서명은 맞는데 회원이 없다. 키가 유출됐거나 DB가 초기화된 상황이다
            return new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        UserStatus status = user.getStatus();
        if (status == UserStatus.BLOCKED) {
            return new BusinessException(ErrorCode.USER_BLOCKED);
        }
        if (status == UserStatus.WITHDRAWN) {
            return withdrawn(user);
        }
        return null;
    }

    /** 유예 중이면 언제까지 복구할 수 있는지 알려준다(ADR-054). */
    private BusinessException withdrawn(User user) {
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

    private void reject(HttpServletRequest request, BusinessException e) {
        request.setAttribute(AUTH_ERROR, e);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
