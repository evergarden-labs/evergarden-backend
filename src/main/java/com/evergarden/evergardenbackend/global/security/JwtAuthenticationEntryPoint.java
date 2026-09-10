package com.evergarden.evergardenbackend.global.security;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 인증되지 않은 요청이 보호된 경로에 닿았을 때 응답한다(COMMON-02).
 *
 * <p>{@link JwtAuthenticationFilter}가 적어둔 사유가 있으면 그대로 쓰고, 없으면
 * <b>토큰을 아예 안 보낸 것</b>이므로 {@code UNAUTHENTICATED}다.
 *
 * <pre>
 * 토큰 없음      → 401 UNAUTHENTICATED
 * 만료된 토큰     → 401 TOKEN_EXPIRED     ← 재발급하면 됨
 * 위조·형식 오류  → 401 TOKEN_INVALID     ← 재로그인해야 함
 * 차단된 회원     → 403 USER_BLOCKED
 * 탈퇴한 회원     → 403 USER_WITHDRAWN    ← details.restorableUntil
 * </pre>
 *
 * <p>이름은 "인증 진입점"이지만 {@code 403}도 여기서 나간다. 필터가 <b>인증을 세우지
 * 않는 방식</b>으로 거절하기 때문이다. 상태 코드는 {@link ErrorCode}가 들고 있어
 * 여기서 정하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorResponder responder;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR);
        if (reason instanceof BusinessException e) {
            responder.write(response, e);
            return;
        }
        responder.write(response, ErrorCode.UNAUTHENTICATED);
    }
}
