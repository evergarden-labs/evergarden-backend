package com.evergarden.evergardenbackend.global.exception;

import java.util.Map;
import lombok.Getter;

/**
 * 서비스 규칙에 걸려 요청을 처리하지 못할 때 던진다.
 *
 * <p>{@link GlobalExceptionHandler}가 잡아 {@link ErrorCode}의 상태와 메시지로 응답한다.
 * 스택 트레이스를 만들지 않는다 — 예상된 흐름이라 성능만 축내고 로그를 어지럽힌다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 코드별 부가 정보. 없으면 {@code null} */
    private final transient Map<String, Object> details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    /**
     * @param details 앱이 안내에 쓸 값. 예를 들어 {@code USER_BLOCKED}는 사유와 차단 시점을,
     *                {@code CAPSULE_NOT_UNLOCKABLE}은 해제 조건 종류를 담는다
     */
    public BusinessException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.getMessage(), null, false, false);
        this.errorCode = errorCode;
        this.details = details;
    }
}
