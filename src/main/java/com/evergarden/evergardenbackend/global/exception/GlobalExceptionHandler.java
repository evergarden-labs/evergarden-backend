package com.evergarden.evergardenbackend.global.exception;

import com.evergarden.evergardenbackend.global.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 오류 응답을 한 곳에서 만든다(COMMON-01 ~ COMMON-06).
 *
 * <p>컨트롤러는 {@link BusinessException}만 던지면 된다. 상태 코드와 메시지는
 * {@link ErrorCode}가 들고 있어 여기서 꺼내 쓴다.
 *
 * <p>명세의 생략 규칙(ADR-012)에 따라 {@code 401}·{@code 403}·{@code 500}은
 * 각 오퍼레이션에 적지 않는다. 이 핸들러가 전역으로 책임진다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 서비스 규칙에 걸린 경우. 대부분의 오류가 여기로 온다. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        // 예상된 흐름이므로 스택 트레이스를 남기지 않는다
        log.debug("business error: {} {}", code, e.getDetails());
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, e.getDetails()));
    }

    // ── COMMON-01 잘못된 요청 ─────────────────────────────────────

    /** {@code @Valid} 검증 실패. 어느 필드가 왜 틀렸는지 함께 내려보낸다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErrorResponse.FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        return badRequest(ErrorResponse.ofFieldErrors(ErrorCode.INVALID_REQUEST, fields));
    }

    /** {@code @Validated}가 붙은 파라미터 검증 실패. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        List<ErrorResponse.FieldError> fields = e.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.FieldError(
                        v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return badRequest(ErrorResponse.ofFieldErrors(ErrorCode.INVALID_REQUEST, fields));
    }

    /** 본문이 JSON이 아니거나 타입이 맞지 않는 경우. */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception e) {
        log.debug("malformed request: {}", e.getMessage());
        return badRequest(ErrorResponse.of(ErrorCode.INVALID_REQUEST));
    }

    // ── COMMON-02 인증 · COMMON-03 권한 ───────────────────────────

    /** 토큰이 없거나 유효하지 않은 경우. 필터가 걸러내지 못하고 올라온 것만 여기로 온다. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        log.debug("authentication failed: {}", e.getMessage());
        return status(ErrorCode.UNAUTHENTICATED);
    }

    /** 인증은 됐지만 권한이 없는 경우. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        log.debug("access denied: {}", e.getMessage());
        return status(ErrorCode.FORBIDDEN);
    }

    // ── COMMON-04 리소스 없음 ─────────────────────────────────────

    /** 없는 경로로 들어온 경우. 도메인 리소스가 없는 것은 {@link BusinessException}이 따로 던진다. */
    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handleNoResource(Exception e) {
        log.debug("no resource: {}", e.getMessage());
        return status(ErrorCode.NOT_FOUND);
    }

    // ── COMMON-06 서버 오류 ───────────────────────────────────────

    /**
     * 위에서 걸리지 않은 모든 예외.
     *
     * <p>여기로 오는 것은 전부 예상하지 못한 오류다. 스택 트레이스를 남기고,
     * <b>사용자에게는 내부 사정을 알리지 않는다</b> — 예외 메시지에 테이블 이름이나
     * 쿼리가 섞여 나올 수 있다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("unhandled exception", e);
        return status(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ErrorResponse> status(ErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }

    private ResponseEntity<ErrorResponse> badRequest(ErrorResponse body) {
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus()).body(body);
    }
}
