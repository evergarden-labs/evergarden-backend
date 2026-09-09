package com.evergarden.evergardenbackend.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * API가 돌려줄 수 있는 모든 오류 코드.
 *
 * <p><b>이 목록은 {@code docs/error-codes.md}에서 그대로 옮긴 것이다.</b>
 * 코드를 새로 만들어야 하면 문서를 먼저 고치고 여기에 반영한다 —
 * 반대로 하면 명세와 구현이 갈라진다.
 *
 * <p>{@code message}는 사용자에게 그대로 보여줄 수 있는 문장이다.
 * 개발자용 설명은 문서에 있고, 여기에는 두지 않는다.
 */
@Getter
public enum ErrorCode {

    // ── 400 — 잘못된 요청 (COMMON-01) ──
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값을 확인해 주세요."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "시작일이 종료일보다 늦을 수 없습니다."),
    INVALID_SHARE_TARGET(HttpStatus.BAD_REQUEST, "공유할 코스나 아카이브를 선택해 주세요."),
    REGION_REQUIRED(HttpStatus.BAD_REQUEST, "여행 지역을 선택해 주세요."),
    INVALID_UNLOCK_CONDITION(HttpStatus.BAD_REQUEST, "해제 조건은 날짜와 위치 중 하나만 정할 수 있습니다."),
    RESTORE_PERIOD_EXPIRED(HttpStatus.BAD_REQUEST, "복구할 수 있는 기간이 지났습니다. 새로 가입해 주세요."),
    INVALID_MEDIA_FORMAT(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
    MEDIA_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일 용량이 너무 큽니다."),
    MEDIA_UPLOAD_INCOMPLETE(HttpStatus.BAD_REQUEST, "업로드가 완료되지 않았습니다. 다시 시도해 주세요."),
    LOCATION_MISMATCH(HttpStatus.BAD_REQUEST, "현재 위치가 인증하려는 지역과 다릅니다."),
    LOCATION_ACCURACY_TOO_LOW(HttpStatus.BAD_REQUEST, "위치를 정확히 확인할 수 없습니다. 실외에서 다시 시도해 주세요."),
    REGION_NOT_DETERMINED(HttpStatus.BAD_REQUEST, "현재 위치의 지역을 확인할 수 없습니다."),

    // ── 401 — 인증 오류 (COMMON-02) ──
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),
    SOCIAL_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다."),
    ADMIN_CREDENTIALS_INVALID(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),

    // ── 403 — 권한 오류 (COMMON-03) ──
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_RESOURCE_OWNER(HttpStatus.FORBIDDEN, "내 기록만 수정하거나 삭제할 수 있습니다."),
    NOT_COLLABORATOR(HttpStatus.FORBIDDEN, "이 아카이브를 편집할 권한이 없습니다."),
    COLLABORATION_CLOSED(HttpStatus.FORBIDDEN, "공동 편집이 종료되어 더 이상 편집할 수 없습니다."),
    USER_BLOCKED(HttpStatus.FORBIDDEN, "이용이 제한된 계정입니다."),
    USER_WITHDRAWN(HttpStatus.FORBIDDEN, "탈퇴한 계정입니다."),
    ADMIN_ONLY(HttpStatus.FORBIDDEN, "관리자만 접근할 수 있습니다."),

    // ── 404 — 리소스 없음 (COMMON-04) ──
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "여행 일정을 찾을 수 없습니다."),
    TRIP_PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "일정에 담긴 장소를 찾을 수 없습니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "관광지를 찾을 수 없습니다."),
    REGION_NOT_FOUND(HttpStatus.NOT_FOUND, "지역을 찾을 수 없습니다."),
    ARCHIVE_NOT_FOUND(HttpStatus.NOT_FOUND, "아카이브를 찾을 수 없습니다."),
    ARCHIVE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "아카이브 항목을 찾을 수 없습니다."),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "사진 또는 영상을 찾을 수 없습니다."),
    TIME_CAPSULE_NOT_FOUND(HttpStatus.NOT_FOUND, "타임캡슐을 찾을 수 없습니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시물을 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "신고를 찾을 수 없습니다."),
    REGION_VISIT_NOT_FOUND(HttpStatus.NOT_FOUND, "방문 인증 기록을 찾을 수 없습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 데이터를 찾을 수 없습니다."),

    // ── 409 — 중복 요청·상태 충돌 (COMMON-05) ──
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "이미 처리된 요청입니다."),
    ALREADY_LIKED(HttpStatus.CONFLICT, "이미 좋아요한 게시물입니다."),
    NOT_LIKED(HttpStatus.CONFLICT, "좋아요하지 않은 게시물입니다."),
    ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 대상입니다."),
    ALREADY_INVITED(HttpStatus.CONFLICT, "이미 초대한 사용자입니다."),
    ALREADY_COLLABORATOR(HttpStatus.CONFLICT, "이미 참여 중인 아카이브입니다."),
    TRIP_ARCHIVE_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 다른 기록과 연결되어 있습니다."),
    SOCIAL_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 가입에 사용된 소셜 계정입니다."),
    ONBOARDING_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 초기 설정을 마쳤습니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    CAPSULE_NOT_UNLOCKABLE(HttpStatus.CONFLICT, "아직 열 수 없는 타임캡슐입니다."),
    CAPSULE_ALREADY_OPENED(HttpStatus.CONFLICT, "이미 열어본 타임캡슐입니다."),
    REPORT_ALREADY_REVIEWED(HttpStatus.CONFLICT, "이미 처리된 신고입니다."),

    // ── 500 — 서버 오류 (COMMON-06) ──
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),

    // ── 503 — 외부 서비스 장애 (ADR-013) ──
    TOUR_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "관광 정보를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
