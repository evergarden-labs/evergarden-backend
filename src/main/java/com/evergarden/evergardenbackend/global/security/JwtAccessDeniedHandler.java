package com.evergarden.evergardenbackend.global.security;

import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 인증은 됐지만 권한이 모자랄 때 응답한다(COMMON-03).
 *
 * <p>지금 시큐리티가 거는 권한 규칙은 {@code /admin/**} 하나뿐이라 사실상 전부
 * {@code ADMIN_ONLY}다. 그 밖의 거절은 {@code FORBIDDEN}으로 떨어뜨린다 —
 * 오류 코드 표가 예비용으로 남겨둔 코드다.
 *
 * <p>리소스 소유권({@code NOT_RESOURCE_OWNER})처럼 <b>데이터를 봐야 아는 권한</b>은
 * 여기서 판단할 수 없다. 서비스가 {@code BusinessException}으로 던지고
 * {@code GlobalExceptionHandler}가 받는다.
 */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityErrorResponder responder;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException e) throws IOException {
        responder.write(response, isAdminPath(request) ? ErrorCode.ADMIN_ONLY : ErrorCode.FORBIDDEN);
    }

    /** 컨텍스트 경로를 뺀 실제 경로로 본다. {@code server.servlet.context-path}가 붙어도 안전하다. */
    private boolean isAdminPath(HttpServletRequest request) {
        return request.getRequestURI()
                .substring(request.getContextPath().length())
                .startsWith("/admin/");
    }
}
