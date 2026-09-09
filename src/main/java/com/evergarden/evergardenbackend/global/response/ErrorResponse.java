package com.evergarden.evergardenbackend.global.response;

import com.evergarden.evergardenbackend.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * 모든 실패 응답의 봉투(ADR-008).
 *
 * <pre>{@code { "error": { "code": ..., "message": ..., "details": ... } }}</pre>
 *
 * <p>성공 봉투({@code data}/{@code meta})와 필드가 겹치지 않아
 * 클라이언트가 한 번에 성공·실패를 가른다.
 */
public record ErrorResponse(Body error) {

    /**
     * @param code    {@code docs/error-codes.md}에 정의된 코드만 쓴다
     * @param message 사용자에게 그대로 보여줄 수 있는 문장
     * @param details 코드별 부가 정보. 없으면 {@code null}
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Body(String code, String message, Map<String, Object> details) {
    }

    /** 검증 실패에서 필드별 사유를 담는 모양. {@code details.fields[]}에 들어간다. */
    public record FieldError(String field, String reason) {
    }

    public static ErrorResponse of(ErrorCode code) {
        return new ErrorResponse(new Body(code.name(), code.getMessage(), null));
    }

    public static ErrorResponse of(ErrorCode code, Map<String, Object> details) {
        return new ErrorResponse(new Body(code.name(), code.getMessage(), details));
    }

    /** 검증 실패 전용. 어느 필드가 왜 틀렸는지 함께 내려보낸다. */
    public static ErrorResponse ofFieldErrors(ErrorCode code, List<FieldError> fields) {
        return new ErrorResponse(new Body(code.name(), code.getMessage(), Map.of("fields", fields)));
    }
}
