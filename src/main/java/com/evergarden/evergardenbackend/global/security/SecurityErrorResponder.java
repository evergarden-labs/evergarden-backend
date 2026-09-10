package com.evergarden.evergardenbackend.global.security;

import com.evergarden.evergardenbackend.global.exception.BusinessException;
import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.evergarden.evergardenbackend.global.response.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 시큐리티 필터 단계에서 오류 응답을 직접 쓴다.
 *
 * <p>필터는 {@code DispatcherServlet} <b>앞</b>에 있어 여기서 막힌 요청은
 * {@code @RestControllerAdvice}까지 가지 못한다. 그래서 같은 {@link ErrorResponse}를
 * 여기서 한 번 더 만들어 내보낸다 — <b>봉투 모양을 맞추기 위해서다.</b>
 *
 * <pre>{@code {"error":{"code":"UNAUTHENTICATED","message":"로그인이 필요합니다.","details":null}}}</pre>
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponder {

    /** 스프링이 응답을 직렬화할 때 쓰는 그 매퍼다. 새로 만들면 설정이 어긋난다.
     *  Boot 4는 Jackson 3을 쓰므로 타입이 {@code tools.jackson}이다. */
    private final JsonMapper jsonMapper;

    public void write(HttpServletResponse response, BusinessException e) throws IOException {
        write(response, e.getErrorCode(), ErrorResponse.of(e.getErrorCode(), e.getDetails()));
    }

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        write(response, code, ErrorResponse.of(code));
    }

    private void write(HttpServletResponse response, ErrorCode code, ErrorResponse body)
            throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getWriter(), body);
    }
}
