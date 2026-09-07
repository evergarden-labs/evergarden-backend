# 오류 코드 표 (초안)

> **상태: 유저 스토리 카탈로그(88행) 확정본 기준** · 최종 수정 2026-09-07
>
> 이 문서는 `evergardenapi.yaml`을 작성하기 위한 **근거 문서**입니다.
> API 명세는 여기 있는 코드만 사용하며, 여기 없는 코드를 새로 만들지 않습니다.
> 새 코드가 필요하면 **이 문서를 먼저 고치고** 명세를 갱신합니다.
>
> 근거: 유저 스토리 카탈로그(102행) · ERD(테이블 25개) · COMMON-01 ~ COMMON-07

---

## 1. 오류 응답 형식

모든 실패 응답은 아래 봉투를 사용합니다. 성공 봉투(`{data, meta}`)와 필드가 겹치지 않습니다.

```json
{
  "error": {
    "code": "TRIP_NOT_FOUND",
    "message": "여행 일정을 찾을 수 없습니다.",
    "details": null
  }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `code` | string | 이 문서에 정의된 코드. `SCREAMING_SNAKE_CASE` |
| `message` | string | 사용자에게 그대로 보여줄 수 있는 한국어 문장 |
| `details` | object \| null | 코드별 부가 정보. 없으면 `null` |

### `details` 규약

`details`의 형태는 코드마다 다르며, 형태가 정해진 코드는 아래 목록의 비고에 적었습니다.
검증 실패(`INVALID_REQUEST`)만 형태가 고정되어 있습니다.

```json
{
  "details": {
    "fields": [
      { "field": "startDate", "reason": "종료일보다 늦을 수 없습니다." }
    ]
  }
}
```

---

## 2. HTTP 상태 매핑

유저 스토리 COMMON-01 ~ COMMON-07을 HTTP 상태에 1:1로 옮긴 것입니다.

| 유저 스토리 | HTTP | 분류 |
|---|---|---|
| COMMON-01 잘못된 요청 처리 | `400` | 입력값·요청 형식 오류 |
| COMMON-02 인증 오류 처리 | `401` | 인증되지 않음 · 토큰 만료 |
| COMMON-03 권한 오류 처리 | `403` | 인증됐지만 권한 없음 |
| COMMON-04 리소스 없음 처리 | `404` | 대상이 없거나 삭제됨 |
| COMMON-05 중복 요청 처리 | `409` | 중복 요청 · 현재 상태와 충돌 |
| COMMON-06 서버 오류 처리 | `500` | 예기치 않은 서버 오류 |
| COMMON-07 네트워크 오류 처리 | — | **클라이언트 영역.** 서버 응답 코드가 없습니다 |

> **[결정 A] 외부 API 장애는 `503`으로 둡니다** — 2026-09-07, 백엔드 재량으로 확정
> COMMON 표에 분류가 없는 건이었습니다. 재시도가 무의미한 `500`과 달리
> 클라이언트가 "잠시 후 다시 시도"로 안내해야 해서 구분했습니다.

> **[결정 B] `413` · `415`를 쓰지 않고 `400`으로 흡수합니다** — 2026-09-07, 백엔드 재량으로 확정
> COMMON-01~07이 정의한 7가지 분류 밖으로 상태 코드를 늘리지 않기로 했습니다.
> 클라이언트는 `error.code`로 원인을 구분합니다.

---

## 3. 400 — 잘못된 요청

| 코드 | 상황 | 근거 | 비고 |
|---|---|---|---|
| `INVALID_REQUEST` | 필수값 누락, 타입 불일치, 길이 초과 등 일반 검증 실패 | COMMON-01 | `details.fields[]` 사용 |
| `INVALID_DATE_RANGE` | 시작일이 종료일보다 늦음 | PLAN-01, ARCH-01 | |
| `INVALID_SHARE_TARGET` | 게시물에 코스와 아카이브를 모두 지정하지 않음 | COMM-04 | |
| `REGION_REQUIRED` | 코스 없이 아카이브만 공유하면서 지역을 고르지 않음 | COMM-04 | |
| `INVALID_UNLOCK_CONDITION` | 타임캡슐 해제 조건이 날짜·위치 중 하나로 정해지지 않음 | TC-01 | |
| `INVALID_MEDIA_FORMAT` | 허용하지 않는 파일 형식 | MEDIA-01 | 사진 JPEG·PNG·HEIC·WEBP / 영상 MP4·MOV |
| `MEDIA_TOO_LARGE` | 파일 용량 초과 | MEDIA-01 | 사진 10MB · 영상 200MB(3분) · 1회 20개 |
| `MEDIA_UPLOAD_INCOMPLETE` | 스토리지에 파일이 올라오지 않은 상태로 업로드 완료를 통보함 | MEDIA-01 | presigned URL 방식(ADR-023) |
| `LOCATION_MISMATCH` | 보낸 좌표가 인증하려는 지역에 속하지 않음 | MAP-01, MAP-04 | |
| `LOCATION_ACCURACY_TOO_LOW` | 위치 정확도가 인증 기준에 못 미침 | MAP-04 | 임계값 **100m** |
| `REGION_NOT_DETERMINED` | 좌표로 지역을 판정하지 못함(주변 관광지 없음 등) | MAP-04 | |

> **[결정 C] 용량 상한과 허용 형식** — 2026-09-07, 통상값을 적용
> 사진 **10MB**(JPEG·PNG·HEIC·WEBP) / 영상 **200MB, 최대 3분**(MP4·MOV) / 1회 업로드 **20개**.
> HEIC는 아이폰 기본 포맷이라 반드시 포함합니다.
> 앱이 업로드 전 리사이즈하는 것을 전제로 한 값입니다 — 원본을 그대로 올리면
> 1080p 영상 1분이 약 100MB, 4K는 약 350MB라 상한을 금방 넘습니다.

> **[결정 D] 위치 정확도 임계값은 `100m`** — 2026-09-07
> 단말이 좌표와 함께 보내는 정확도(iOS `horizontalAccuracy`, Android `getAccuracy()`)를 씁니다.
> **이 값은 GPS 조작을 막지 못합니다** — 정확도 자체도 단말이 보내는 값이라 함께 조작됩니다.
> 지하철역처럼 위치가 부정확하게 잡히는 환경을 걸러내는 용도이며,
> 실질적인 어뷰징 방지는 `REGION_VISIT_COOLDOWN`(일주일)이 담당합니다.

---

## 4. 401 — 인증 오류

| 코드 | 상황 | 근거 | 비고 |
|---|---|---|---|
| `UNAUTHENTICATED` | 액세스 토큰이 없음 | COMMON-02 | |
| `TOKEN_EXPIRED` | 액세스 토큰 만료 | AUTH-05 | 클라이언트가 재발급을 시도해야 함 |
| `TOKEN_INVALID` | 서명 불일치, 변조, 형식 오류 | COMMON-02 | 재발급 대상이 아님 |
| `REFRESH_TOKEN_EXPIRED` | 리프레시 토큰 만료·폐기 | AUTH-05 | 재로그인 필요 |
| `SOCIAL_AUTH_FAILED` | 소셜 제공자 인증에 실패 | AUTH-01, AUTH-02 | `details.provider` |
| `ADMIN_CREDENTIALS_INVALID` | 관리자 아이디·비밀번호 불일치 | ADMIN-01 | 어느 쪽이 틀렸는지 구분하지 않음 |

---

## 5. 403 — 권한 오류

| 코드 | 상황 | 근거 | 비고 |
|---|---|---|---|
| `FORBIDDEN` | 분류되지 않은 권한 거부 | COMMON-03 | |
| `NOT_RESOURCE_OWNER` | 남의 일정·아카이브·게시물·댓글·타임캡슐을 수정하거나 삭제 | COMMON-03 | |
| `NOT_COLLABORATOR` | 공동 편집자가 아닌 사용자가 편집 시도 | ARCH-12 | |
| `COLLABORATION_CLOSED` | 종료된 공동 편집 아카이브를 **편집** 시도 | ARCH-14 | 조회는 통과 — ARCH-14 결과 칸에 명문화됨 |
| `USER_BLOCKED` | 차단된 회원의 요청 | ADMIN-05, ADMIN-10 | `details.reason`, `details.blockedAt` |
| `USER_WITHDRAWN` | 탈퇴 처리된 계정의 요청 | AUTH-04 | |
| `ADMIN_ONLY` | 일반 사용자가 관리자 API에 접근 | ADMIN-03 | |

> **[결정 E] 공동 편집 종료 후에도 조회는 허용합니다** — 2026-09-07
> 이 코드는 **쓰기 요청에만** 사용하고 조회는 통과시킵니다. 근거 셋:
> ① ARCH-13이 "종료 후 복제"인데 내용을 못 보면 복제 대상을 확인할 수 없습니다.
> ② 동행자와 함께 만든 앨범을 종료했다는 이유로 못 보게 되면 상실감이 큽니다.
> ③ 완전히 막으면 복제하지 않은 사람은 영영 볼 수 없어 복제를 강요하는 흐름이 됩니다.
> 이 결정은 ARCH-12의 결과 칸에 반영되었습니다.

---

## 6. 404 — 리소스 없음

모두 COMMON-04가 근거이며, 대상 엔티티만 다릅니다.

| 코드 | 대상 |
|---|---|
| `USER_NOT_FOUND` | 회원 |
| `TRIP_NOT_FOUND` | 여행 일정(코스) |
| `TRIP_PLACE_NOT_FOUND` | 일정에 담긴 장소 |
| `PLACE_NOT_FOUND` | 관광지 |
| `REGION_NOT_FOUND` | 지역 코드 |
| `ARCHIVE_NOT_FOUND` | 아카이브 |
| `ARCHIVE_ITEM_NOT_FOUND` | 아카이브에 배치된 항목 |
| `MEDIA_NOT_FOUND` | 사진·영상 |
| `TIME_CAPSULE_NOT_FOUND` | 타임캡슐 |
| `POST_NOT_FOUND` | 게시물 |
| `COMMENT_NOT_FOUND` | 댓글·대댓글 |
| `REPORT_NOT_FOUND` | 신고 |
| `REGION_VISIT_NOT_FOUND` | 방문 인증 기록 |
| `NOTIFICATION_NOT_FOUND` | 알림 |
| `NOT_FOUND` | 위에 해당하지 않는 대상 |

> 삭제된 리소스도 `404`로 응답합니다. 다만 **게시물은 예외**입니다 —
> 코스·아카이브가 삭제돼도 게시물 자체는 남으므로(COMM-17) `200`으로 응답하고
> 본문에 원본이 없음을 표시합니다.

---

## 7. 409 — 중복 요청 · 상태 충돌

| 코드 | 상황 | 근거 | 비고 |
|---|---|---|---|
| `DUPLICATE_REQUEST` | 분류되지 않은 중복 요청 | COMMON-05 | |
| `ALREADY_LIKED` | 이미 좋아요한 게시물에 다시 좋아요 | COMM-07 | |
| `NOT_LIKED` | 좋아요하지 않은 게시물의 좋아요 취소 | COMM-19 | |
| `ALREADY_REPORTED` | 같은 대상을 다시 신고 | COMM-10, COMM-18 | `(reporter, target)` 유니크 |
| `ALREADY_INVITED` | 이미 초대한 사용자를 다시 초대 | ARCH-10 | |
| `ALREADY_COLLABORATOR` | 이미 참여 중인 아카이브에 다시 참여 | ARCH-11 | |
| `TRIP_ARCHIVE_ALREADY_LINKED` | 일정 또는 아카이브에 이미 상대가 연결됨 | ARCH-01, ARCH-17 | `archives.trip_id` 유니크 |
| `SOCIAL_ACCOUNT_ALREADY_LINKED` | 이미 가입에 사용된 소셜 계정 | AUTH-01 | |
| `ONBOARDING_ALREADY_COMPLETED` | 온보딩을 마친 사용자가 다시 초기 설정 | ONB-01, ONB-02 | |
| `NICKNAME_DUPLICATED` | 이미 쓰이고 있는 닉네임 | ONB-01, MY-02 | `users.nickname` UNIQUE(ADR-025) |
| `CAPSULE_NOT_UNLOCKABLE` | 해제 조건이 충족되지 않은 타임캡슐 열기 | TC-05 | `details.unlockType` |
| `CAPSULE_ALREADY_OPENED` | 이미 연 타임캡슐을 다시 열기 | TC-05 | |
| `REPORT_ALREADY_REVIEWED` | 이미 유효·반려 판정된 신고를 다시 판정 | ADMIN-09 | |
| `REGION_VISIT_COOLDOWN` | — | GARDEN-02 | ⚠️ **미사용 후보.** 아래 주석 참고 |

> ⚠️ **`REGION_VISIT_COOLDOWN`은 현재 어느 오퍼레이션에서도 발생하지 않습니다.**
> GARDEN-02의 문구가 "같은 지역은 **7일에 한 번만 성장에 반영된다**"이므로,
> 쿨다운은 **인증 자체를 막는 것이 아니라 보상만 건너뛰는 것**으로 해석했습니다.
> 그래서 `POST /region-visits`는 쿨다운 중에도 `200`으로 성공하고
> 응답의 `rewardStatus: COOLDOWN`으로 알립니다.
> 인증 자체를 거부하기로 정하면 이 코드를 `409`로 되살립니다. 결정 주체: 기획.

> **[결정 F] 같은 지역 재인증 쿨다운은 `7일`** — 2026-09-07, 기획 확정
> ERD의 `user_garden_objects.last_grown_at` 기준으로 판정합니다.
> 다음 인증 가능 시각을 `details.availableAt`으로 내려보내 앱이 안내할 수 있게 합니다.
> 이 결정은 GARDEN-02의 결과 칸에 반영되었습니다.

---

## 8. 500 · 503 — 서버 오류

| 코드 | HTTP | 상황 | 근거 | 비고 |
|---|---|---|---|---|
| `INTERNAL_ERROR` | `500` | 예기치 않은 서버 오류 | COMMON-06 | 상세 내용을 노출하지 않음 |
| `TOUR_API_UNAVAILABLE` | `503` | 콘텐츠랩 API 장애·호출 한도 초과 | PLAN-06/07/08, MAP-03 | [결정 A] |
| `AI_SERVICE_UNAVAILABLE` | `503` | AI 일정 추천 처리 실패 | PLAN-09 | [결정 A] · **구현 방식에 따라 불필요해질 수 있음** |

> **`AI_SERVICE_UNAVAILABLE`은 조건부 코드입니다.**
> PLAN-09는 "가고 싶은 장소들을 입력하면 효율적인 동선 순서로 정리한다"로 확정됐습니다.
> 이를 **좌표 기반 동선 최적화로 자체 계산**하면 호출할 외부 서비스가 없어 이 코드는 발생하지 않습니다.
> 외부 모델을 호출하는 방식으로 정하면 그대로 사용합니다. 결정 주체: 백엔드.

---

## 9. 명세에서 반복하지 않는 공통 응답

`evergardenapi.yaml`에서 아래는 **오퍼레이션마다 적지 않습니다.** 전역 규칙으로 간주합니다.

| 응답 | 적용 범위 | 사용 코드 |
|---|---|---|
| `401` | 인증이 필요한 모든 오퍼레이션 | `UNAUTHENTICATED` · `TOKEN_EXPIRED` · `TOKEN_INVALID` |
| `403` | 인증이 필요한 모든 오퍼레이션 | `USER_BLOCKED` · `USER_WITHDRAWN` |
| `500` | 모든 오퍼레이션 | `INTERNAL_ERROR` |

각 오퍼레이션에는 **그 오퍼레이션에서만 발생하는** `400` · `403` · `404` · `409` · `503`만 적습니다.
인증이 필요 없는 오퍼레이션(`POST /auth/social/{provider}`, `POST /admin/auth/login`)은
위 `401` · `403` 규칙에서 제외됩니다.

---

## 10. 이 문서에 없는 것

**결정의 배경과 근거는 여기 없습니다.** `docs/decisions.md`(ADR)를 보세요.

이 문서는 "이 상황에 어떤 코드를 쓰는가"만 다룹니다.
"왜 임계값을 100m로 정했는가", "왜 `413`을 안 쓰는가" 같은 판단은 ADR에 있습니다.
본문 곳곳의 `[결정 X]` 블록은 그 코드를 쓸 때 바로 알아야 하는 값만 요약한 것이며,
전체 맥락은 아래 대응표를 따라가면 됩니다.

| 코드 | 관련 결정 |
|---|---|
| `TOUR_API_UNAVAILABLE` · `AI_SERVICE_UNAVAILABLE` | ADR-013 (외부 API 장애는 `503`) |
| `MEDIA_TOO_LARGE` · `INVALID_MEDIA_FORMAT` | ADR-014 (`413`·`415` 미사용) · ADR-015 (상한값) |
| `LOCATION_ACCURACY_TOO_LOW` | ADR-016 (임계값 100m) |
| `COLLABORATION_CLOSED` | ADR-017 (종료 후 조회 허용) |
| `REGION_VISIT_COOLDOWN` | ADR-018 (쿨다운 7일) |
| `409` 계열 전반 | ADR-006 (중복은 DB 제약으로 차단) |
| `NICKNAME_DUPLICATED` (미채택) | ADR 문서 D절 — 닉네임 중복 허용 여부 미정 |

**총 55개 코드** — 400: 11 · 401: 6 · 403: 7 · 404: 15 · 409: 14 · 500·503: 3
(이 중 `REGION_VISIT_COOLDOWN` 1개는 현재 미사용 상태입니다.)
