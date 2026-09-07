# ERD

> 최종 수정 2026-09-07 · 근거 문서 5종 중 하나
>
> 테이블 25개. 모든 테이블은 `created_at`·`updated_at`을 가지며 아래 표에서는 생략했습니다
> (`post_likes`처럼 수정될 일이 없는 테이블은 `created_at`만).
>
> 설계 근거는 `docs/decisions.md`(ADR)에 있습니다. 각 컬럼의 비고에 ADR 번호를 달았습니다.

## 저장소에 두지 않는 것

| 데이터 | 어디에 | 왜 |
|---|---|---|
| 리프레시 토큰 | Redis (`jti → user_id`, TTL 7일) | 만료 관리를 TTL에 맡깁니다 |
| 콘텐츠랩 상세 응답 | Redis (짧은 TTL) | `places`에는 자주 쓰는 필드만 적재합니다 |
| 사진·영상 원본 | S3 | `media.storage_key`로 참조 (ADR-023) |

---

## `users` · 서비스 회원

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `nickname` | VARCHAR(20) | UK | N | 한글·영문·숫자만 (ADR-053) · 중복 불가 (ADR-025) · 건너뛴 사용자는 여행자+숫자 (ADR-050) |
| `profile_image_url` | TEXT |  | Y |  |
| `status` | VARCHAR(20) |  | N | ACTIVE / WARNED / BLOCKED / WITHDRAWN |
| `valid_report_count` | INT |  | N | 유효 판정된 누적 신고. 1이면 경고, 3이면 차단 (ADMIN-10) |
| `onboarding_completed` | BOOLEAN |  | N | 기본값 false |
| `withdrawn_at` | TIMESTAMPTZ |  | Y | 탈퇴 시각. **+30일이 복구 기한** (ADR-054). 지나면 배치가 닉네임·프로필을 익명화 |

## `social_accounts` · 연결된 소셜 계정

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `user_id` | BIGINT | FK | N | → users |
| `provider` | VARCHAR(10) |  | N | GOOGLE / KAKAO / NAVER |
| `provider_user_id` | VARCHAR(100) |  | N | 제공자가 주는 고유 식별자 |
| `(provider, provider_user_id)` | — | UK |  | 같은 소셜 계정으로 두 번 가입 불가 |

## `admins` · 관리자 계정

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `login_id` | VARCHAR(50) | UK | N |  |
| `password_hash` | VARCHAR(100) |  | N | BCrypt |
| `name` | VARCHAR(30) |  | N | 제재 이력에 남길 담당자 이름 |

## `regions` · 시/도 · 시군구 마스터 `TourAPI`

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `code` | VARCHAR(10) | PK | N | 콘텐츠랩 areaCode / sigunguCode 원본 (ADR-004) |
| `parent_code` | VARCHAR(10) | FK | Y | → regions. 시/도는 NULL |
| `level` | VARCHAR(10) |  | N | SIDO / SIGUNGU |
| `name` | VARCHAR(50) |  | N |  |
| `center_lat` | NUMERIC(10,7) |  | N | 지도 이동·근사 판정용 |
| `center_lng` | NUMERIC(10,7) |  | N |  |
| `synced_at` | TIMESTAMPTZ |  | N | 마지막 동기화 시각 |

## `places` · 관광지 캐시 `TourAPI`

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `content_id` | VARCHAR(20) | UK | N | 콘텐츠랩 contentid |
| `content_type_id` | VARCHAR(10) |  | Y | 관광지·음식점·숙박 등 분류 |
| `title` | VARCHAR(200) |  | N |  |
| `addr` | VARCHAR(300) |  | Y |  |
| `tel` | VARCHAR(50) |  | Y |  |
| `lat` | NUMERIC(10,7) |  | N | 동선 계산·주변 추천에 사용 |
| `lng` | NUMERIC(10,7) |  | N |  |
| `region_code` | VARCHAR(10) | FK | N | → regions. 게시물 지역 추출의 출발점 |
| `thumbnail_url` | TEXT |  | Y |  |
| `overview` | TEXT |  | Y | 콘텐츠랩 소개글 |
| `use_time` | TEXT |  | Y | 이용 시간. 원문 그대로 (ADR-049) |
| `rest_date` | TEXT |  | Y | 휴무일. 원문 그대로 (ADR-049) |
| `synced_at` | TIMESTAMPTZ |  | N |  |

## `trips` · 여행 일정 = 공유 대상인 코스

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `user_id` | BIGINT | FK | N | → users |
| `title` | VARCHAR(60) |  | N |  |
| `start_date` | DATE |  | N |  |
| `end_date` | DATE |  | N |  |
| `origin_trip_id` | BIGINT | FK | Y | → trips. 복제 계보 (ADR-005). ON DELETE SET NULL |

## `trip_regions` · 일정에 지정한 여행지 (복수)

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `trip_id` | BIGINT | PK,FK | N | → trips |
| `region_code` | VARCHAR(10) | PK,FK | N | → regions |

## `trip_places` · 일자별 방문 장소와 순서

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `trip_id` | BIGINT | FK | N | → trips. ON DELETE CASCADE |
| `place_id` | BIGINT | FK | N | → places |
| `day_number` | SMALLINT |  | N | 여행 1일차 = 1 |
| `sort_order` | SMALLINT |  | N | 그 날의 방문 순서 |
| `memo` | TEXT |  | Y |  |
| `(trip_id, day_number, sort_order)` | — | UK |  | 순서 중복 방지 |

## `region_visits` · 지역 방문 인증 기록

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `user_id` | BIGINT | FK | N | → users |
| `region_code` | VARCHAR(10) | FK | N | → regions |
| `lat` | NUMERIC(10,7) |  | N | 인증 시점 좌표. 어뷰징 조사 근거 |
| `lng` | NUMERIC(10,7) |  | N |  |
| `accuracy_meters` | NUMERIC(6,1) |  | Y | 단말이 보낸 정확도. 100m 초과면 거절 (ADR-016) |
| `verified_at` | TIMESTAMPTZ |  | N | UK 없음 — 재방문 가능해야 정원이 자람 |
| `INDEX(user_id, region_code, verified_at)` | — | IDX |  |  |

## `garden_objects` · 식물·오브젝트 도감 (마스터)

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `region_code` | VARCHAR(10) | FK | Y | → regions. 지역 전용이면 지정, 공통이면 NULL |
| `name` | VARCHAR(50) |  | N |  |
| `type` | VARCHAR(10) |  | N | PLANT / OBJECT |
| `max_stage` | SMALLINT |  | N | 더 자랄 수 없는 단계 |
| `image_url` | TEXT |  | Y |  |

## `user_garden_objects` · 해금하고 키운 것

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `user_id` | BIGINT | FK | N | → users |
| `garden_object_id` | BIGINT | FK | N | → garden_objects |
| `stage` | SMALLINT |  | N | 현재 성장 단계. 기본값 1 |
| `position_x` | SMALLINT |  | Y | 정원 안 배치 좌표 |
| `position_y` | SMALLINT |  | Y |  |
| `unlocked_at` | TIMESTAMPTZ |  | N |  |
| `last_grown_at` | TIMESTAMPTZ |  | Y | 7일 쿨다운 판정 기준 (ADR-018) |
| `(user_id, garden_object_id)` | — | UK |  | 같은 식물 두 번 해금 불가 |

## `media` · 업로드한 사진과 영상

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `user_id` | BIGINT | FK | N | 업로더 |
| `status` | VARCHAR(10) |  | N | PENDING / READY. READY만 다른 도메인에서 사용 가능 (ADR-023) |
| `type` | VARCHAR(10) |  | N | IMAGE / VIDEO |
| `storage_key` | TEXT |  | N | S3 오브젝트 키 |
| `url` | TEXT |  | Y | PENDING이면 아직 없음 |
| `thumbnail_url` | TEXT |  | Y | 사진 리사이즈본. **영상은 항상 NULL** (ADR-052) |
| `width` | INT |  | Y |  |
| `height` | INT |  | Y |  |
| `duration_ms` | INT |  | Y | 영상만. 앱이 보낸 값 (ADR-052) |
| `size_bytes` | BIGINT |  | Y | 사진 10MB / 영상 200MB 상한 (ADR-015) |
| `taken_at` | TIMESTAMPTZ |  | Y | EXIF 촬영 시각. 아카이브 기간의 재료 (ADR-030) |
| `lat` | NUMERIC(10,7) |  | Y | EXIF 촬영 좌표 |
| `lng` | NUMERIC(10,7) |  | Y |  |

## `time_capsules` · 봉인한 기록

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `user_id` | BIGINT | FK | N | → users |
| `title` | VARCHAR(60) |  | N |  |
| `content` | TEXT |  | N | OPENED 전에는 API로 내보내지 않음 |
| `unlock_type` | VARCHAR(10) |  | N | DATE / LOCATION |
| `unlock_date` | DATE |  | Y | DATE일 때만 |
| `unlock_lat` | NUMERIC(10,7) |  | Y | LOCATION일 때만 |
| `unlock_lng` | NUMERIC(10,7) |  | Y |  |
| `unlock_radius_m` | INT |  | Y |  |
| `place_name` | VARCHAR(60) |  | Y | 사용자가 알아볼 장소 이름 |
| `status` | VARCHAR(12) |  | N | SEALED → UNLOCKABLE → OPENED |
| `opened_at` | TIMESTAMPTZ |  | Y |  |

## `time_capsule_media` · 캡슐에 담은 사진

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `capsule_id` | BIGINT | PK,FK | N | → time_capsules. ON DELETE CASCADE |
| `media_id` | BIGINT | PK,FK | N | → media |
| `sort_order` | SMALLINT |  | N |  |

## `archives` · 테마 앨범

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `owner_user_id` | BIGINT | FK | N | → users |
| `trip_id` | BIGINT | FK,UK | Y | → trips. ON DELETE SET NULL. 1:1이되 각자 독립 (ADR-001) |
| `title` | VARCHAR(60) |  | N |  |
| `theme` | VARCHAR(12) |  | N | BOOK / POLAROID / ALBUM / SCRAPBOOK |
| `primary_color` | VARCHAR(7) |  | Y | #RRGGBB |
| `cover_item_id` | BIGINT | FK | Y | → archive_items. 대표 사진 (ARCH-08) |
| `start_date` | DATE |  | Y | 파생값 — 담긴 사진 taken_at의 최솟값 (ADR-030) |
| `end_date` | DATE |  | Y | 파생값 — 담긴 사진 taken_at의 최댓값 |
| `collaboration_status` | VARCHAR(10) |  | N | NONE / OPEN / CLOSED |
| `origin_archive_id` | BIGINT | FK | Y | → archives. 복제 계보 (ADR-005) |

## `archive_items` · 배치된 사진 한 장

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `archive_id` | BIGINT | FK | N | → archives. ON DELETE CASCADE |
| `media_id` | BIGINT | FK | N | → media |
| `sort_order` | SMALLINT |  | N |  |
| `layout` | JSONB |  | N | {x, y, width, height, rotation} — 캔버스 폭 기준 비율 |
| `caption` | VARCHAR(300) |  | Y | 사진에 붙인 짧은 글 (ADR-029) |

## `archive_collaborators` · 공동 편집자

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `archive_id` | BIGINT | FK | N | → archives |
| `user_id` | BIGINT | FK | N | → users |
| `role` | VARCHAR(10) |  | N | OWNER / EDITOR |
| `status` | VARCHAR(10) |  | N | INVITED → JOINED → LEFT (ARCH-13 나가기) |
| `invited_at` | TIMESTAMPTZ |  | N |  |
| `joined_at` | TIMESTAMPTZ |  | Y |  |
| `(archive_id, user_id)` | — | UK |  | 중복 초대 방지 |

## `posts` · 공유 게시물

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `user_id` | BIGINT | FK | N | 작성자 |
| `content` | TEXT |  | N | 원본이 사라져도 남는 본문 |
| `share_type` | VARCHAR(10) |  | N | COURSE / ARCHIVE / BOTH. 원본 삭제 후에도 남음 (ADR-022) |
| `trip_id` | BIGINT | FK | Y | → trips. ON DELETE SET NULL |
| `archive_id` | BIGINT | FK | Y | → archives. ON DELETE SET NULL |
| `like_count` | INT |  | N | 인기 피드 정렬용 비정규화 (ADR-044) |
| `comment_count` | INT |  | N | 대댓글 포함 |
| `status` | VARCHAR(10) |  | N | ACTIVE / DELETED |
| `CHECK 제약` | — | - |  | 걸지 말 것 — SET NULL과 충돌해 삭제가 실패함 (ADR-002) |
| `INDEX(status, created_at)` | — | IDX |  | 최신 피드 |
| `INDEX(status, created_at, like_count)` | — | IDX |  | 인기 피드 (최근 30일) |

## `post_regions` · 게시물이 걸리는 지역 (스냅샷)

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `post_id` | BIGINT | PK,FK | N | → posts |
| `region_code` | VARCHAR(10) | PK,FK | N | → regions |
| `source` | VARCHAR(10) |  | N | COURSE(자동 추출) / MANUAL(작성자 선택) (ADR-003) |

## `post_likes` · 좋아요

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `post_id` | BIGINT | PK,FK | N | → posts |
| `user_id` | BIGINT | PK,FK | N | → users. 복합키가 중복 좋아요를 막음 (ADR-006) |
| `created_at` | TIMESTAMPTZ |  | N | 좋아요 목록 정렬용 |

## `comments` · 댓글과 대댓글

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `post_id` | BIGINT | FK | N | → posts |
| `user_id` | BIGINT | FK | N | 작성자 |
| `parent_comment_id` | BIGINT | FK | Y | → comments. 대댓글이면 채움. 1단계까지만 |
| `content` | TEXT |  | Y | 삭제되면 NULL (자리는 남김, ADR-007) |
| `status` | VARCHAR(10) |  | N | ACTIVE / DELETED |

## `reports` · 접수된 신고

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `reporter_user_id` | BIGINT | FK | N | 신고한 사람 |
| `target_type` | VARCHAR(10) |  | N | POST / COMMENT |
| `target_id` | BIGINT |  | N | 다형 참조라 FK 없음 |
| `target_user_id` | BIGINT | FK | N | 신고당한 작성자. 누적 집계 기준 |
| `reason` | VARCHAR(12) |  | N | OBSCENE / ABUSE / SPAM / FALSE_INFO / ETC (ADR-046) |
| `detail` | VARCHAR(200) |  | Y | 덧붙인 설명. ETC이면 필수 (ADR-046) |
| `status` | VARCHAR(10) |  | N | PENDING → VALID / REJECTED |
| `reviewed_by` | BIGINT | FK | Y | → admins |
| `reviewed_at` | TIMESTAMPTZ |  | Y |  |
| `note` | VARCHAR(200) |  | Y | 관리자 판정 메모 |
| `(reporter_user_id, target_type, target_id)` | — | UK |  | 반복 신고 방지 (ADR-006) |

## `sanctions` · 경고·차단·해제 이력

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `user_id` | BIGINT | FK | N | 제재 대상 |
| `type` | VARCHAR(10) |  | N | WARNING / BLOCK / UNBLOCK |
| `source` | VARCHAR(10) |  | N | MANUAL(관리자 재량) / AUTO(누적 도달) |
| `reason` | VARCHAR(200) |  | N |  |
| `issued_by` | BIGINT | FK | Y | → admins. 자동 제재면 NULL |
| `만료 컬럼` | — | - |  | 없음 — 차단은 무기한 (ADR-033) |

## `notifications` · 받은 알림

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `id` | BIGSERIAL | PK | N |  |
| `user_id` | BIGINT | FK | N | 수신자 |
| `type` | VARCHAR(20) |  | N | COLLAB_INVITE / CAPSULE_UNLOCK / WARNING |
| `title` | VARCHAR(100) |  | N |  |
| `body` | TEXT |  | Y |  |
| `target_type` | VARCHAR(20) |  | Y | ARCHIVE / TIME_CAPSULE / SANCTION |
| `target_id` | BIGINT |  | Y | 눌렀을 때 이동할 대상 |
| `is_read` | BOOLEAN |  | N | 기본값 false |
| `INDEX(user_id, is_read)` | — | IDX |  | 안 읽은 개수 조회 (NOTI-06) |

## `notification_settings` · 알림 종류별 수신 여부

| 컬럼 | 타입 | 키 | NULL | 비고 |
|---|---|---|---|---|
| `user_id` | BIGINT | PK,FK | N | → users |
| `type` | VARCHAR(20) | PK | N | 알림 종류와 1:1 (ADR-047) |
| `enabled` | BOOLEAN |  | N | WARNING은 끌 수 없음 |
