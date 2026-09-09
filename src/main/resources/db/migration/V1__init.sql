-- 에버가든 초기 스키마 (테이블 25개)
-- 근거: docs/erd.md · docs/decisions.md
--
-- 규칙
--   * enum은 PostgreSQL enum 타입 대신 VARCHAR + CHECK로 둔다.
--     JPA에서 다루기 쉽고, 값을 추가할 때 타입을 변경하지 않아도 된다.
--   * created_at은 DB 기본값으로 채우고, updated_at은 JPA 감사(@LastModifiedDate)가 채운다.
--   * 좌표는 numeric(10,7). PostGIS를 쓰지 않는다 (ADR-027).

-- ============================================================
-- 사용자 · 인증
-- ============================================================

CREATE TABLE users (
    id                   BIGSERIAL     PRIMARY KEY,
    nickname             VARCHAR(20)  NOT NULL,
    profile_image_url    TEXT,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    valid_report_count   INT          NOT NULL DEFAULT 0,
    onboarding_completed BOOLEAN      NOT NULL DEFAULT FALSE,
    withdrawn_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_nickname_uk  UNIQUE (nickname),
    CONSTRAINT users_status_chk   CHECK (status IN ('ACTIVE','WARNED','BLOCKED','WITHDRAWN')),
    -- 한글·영문·숫자만, 1~20자 (ADR-053)
    CONSTRAINT users_nickname_chk CHECK (nickname ~ '^[가-힣a-zA-Z0-9]{1,20}$')
);
COMMENT ON TABLE  users IS '서비스 회원. 소셜 로그인 전용이라 비밀번호 컬럼이 없다';
COMMENT ON COLUMN users.valid_report_count IS '유효 판정된 누적 신고. 1이면 경고, 3이면 차단 (ADMIN-10)';
COMMENT ON COLUMN users.withdrawn_at IS '탈퇴 시각. +30일이 복구 기한 (ADR-054)';

CREATE TABLE social_accounts (
    id               BIGSERIAL     PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    provider         VARCHAR(10)  NOT NULL,
    provider_user_id VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT social_accounts_user_fk  FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT social_accounts_uk       UNIQUE (provider, provider_user_id),
    CONSTRAINT social_accounts_prov_chk CHECK (provider IN ('GOOGLE','KAKAO','NAVER'))
);
CREATE INDEX social_accounts_user_idx ON social_accounts (user_id);

CREATE TABLE admins (
    id            BIGSERIAL     PRIMARY KEY,
    login_id      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    name          VARCHAR(30)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT admins_login_id_uk UNIQUE (login_id)
);
COMMENT ON TABLE admins IS '관리자 계정. 일반 회원과 저장소를 분리해 소셜 전용 정책을 지킨다 (ADR-036)';

-- ============================================================
-- 지역 · 장소 (한국관광 콘텐츠랩에서 동기화)
-- ============================================================

CREATE TABLE regions (
    code        VARCHAR(10)   PRIMARY KEY,
    parent_code VARCHAR(10),
    level       VARCHAR(10)   NOT NULL,
    name        VARCHAR(50)   NOT NULL,
    center_lat  NUMERIC(10,7) NOT NULL,
    center_lng  NUMERIC(10,7) NOT NULL,
    synced_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT regions_parent_fk FOREIGN KEY (parent_code) REFERENCES regions(code),
    CONSTRAINT regions_level_chk CHECK (level IN ('SIDO','SIGUNGU')),
    -- level과 parent_code가 어긋나지 않게 한다.
    -- 동기화 배치가 시군구를 넣으면서 상위 시/도를 빠뜨리면 여기서 걸린다
    CONSTRAINT regions_hierarchy_chk CHECK (
        (level = 'SIDO'    AND parent_code IS NULL)
     OR (level = 'SIGUNGU' AND parent_code IS NOT NULL))
);
COMMENT ON TABLE regions IS '콘텐츠랩 areaCode/sigunguCode를 그대로 기본키로 쓴다 (ADR-004)';
CREATE INDEX regions_parent_idx ON regions (parent_code);

CREATE TABLE places (
    id              BIGSERIAL     PRIMARY KEY,
    content_id      VARCHAR(20)   NOT NULL,
    content_type_id VARCHAR(10),
    title           VARCHAR(200)  NOT NULL,
    addr            VARCHAR(300),
    tel             VARCHAR(50),
    lat             NUMERIC(10,7) NOT NULL,
    lng             NUMERIC(10,7) NOT NULL,
    region_code     VARCHAR(10)   NOT NULL,
    thumbnail_url   TEXT,
    overview        TEXT,
    use_time        TEXT,
    rest_date       TEXT,
    synced_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT places_content_id_uk UNIQUE (content_id),
    CONSTRAINT places_region_fk     FOREIGN KEY (region_code) REFERENCES regions(code)
);
COMMENT ON COLUMN places.use_time  IS '이용 시간. 콘텐츠랩 원문 그대로 파싱하지 않는다 (ADR-049)';
COMMENT ON COLUMN places.rest_date IS '휴무일. 콘텐츠랩 원문 그대로';
CREATE INDEX places_region_idx ON places (region_code);
CREATE INDEX places_title_idx  ON places (title);

-- ============================================================
-- 미디어
-- ============================================================

CREATE TABLE media (
    id            BIGSERIAL     PRIMARY KEY,
    user_id       BIGINT        NOT NULL,
    status        VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    type          VARCHAR(10)   NOT NULL,
    storage_key   TEXT          NOT NULL,
    url           TEXT,
    thumbnail_url TEXT,
    width         INT,
    height        INT,
    duration_ms   INT,
    size_bytes    BIGINT,
    taken_at      TIMESTAMPTZ,
    lat           NUMERIC(10,7),
    lng           NUMERIC(10,7),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT media_user_fk   FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT media_status_chk CHECK (status IN ('PENDING','READY')),
    CONSTRAINT media_type_chk   CHECK (type IN ('IMAGE','VIDEO'))
);
COMMENT ON COLUMN media.status        IS 'PENDING은 URL만 발급된 상태. READY만 다른 도메인에서 쓸 수 있다 (ADR-023)';
COMMENT ON COLUMN media.thumbnail_url IS '사진 리사이즈본. 영상은 항상 NULL (ADR-052)';
COMMENT ON COLUMN media.taken_at      IS 'EXIF 촬영 시각. 아카이브 여행 기간의 재료 (ADR-030)';
CREATE INDEX media_user_idx ON media (user_id);

-- ============================================================
-- 플래너 — 여행 일정(코스)
-- ============================================================

CREATE TABLE trips (
    id             BIGSERIAL     PRIMARY KEY,
    user_id        BIGINT      NOT NULL,
    title          VARCHAR(60) NOT NULL,
    start_date     DATE        NOT NULL,
    end_date       DATE        NOT NULL,
    origin_trip_id BIGINT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT trips_user_fk   FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT trips_origin_fk FOREIGN KEY (origin_trip_id) REFERENCES trips(id) ON DELETE SET NULL,
    CONSTRAINT trips_date_chk  CHECK (start_date <= end_date)
);
COMMENT ON COLUMN trips.origin_trip_id IS '남의 코스를 가져와 만든 일정이면 원본. 복제 계보 (ADR-005)';
CREATE INDEX trips_user_idx ON trips (user_id, start_date DESC);

CREATE TABLE trip_regions (
    trip_id     BIGINT      NOT NULL,
    region_code VARCHAR(10) NOT NULL,
    PRIMARY KEY (trip_id, region_code),
    CONSTRAINT trip_regions_trip_fk   FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    CONSTRAINT trip_regions_region_fk FOREIGN KEY (region_code) REFERENCES regions(code)
);

CREATE TABLE trip_places (
    id         BIGSERIAL     PRIMARY KEY,
    trip_id    BIGINT      NOT NULL,
    place_id   BIGINT      NOT NULL,
    day_number SMALLINT    NOT NULL,
    sort_order SMALLINT    NOT NULL,
    memo       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT trip_places_trip_fk  FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    CONSTRAINT trip_places_place_fk FOREIGN KEY (place_id) REFERENCES places(id),
    CONSTRAINT trip_places_order_uk UNIQUE (trip_id, day_number, sort_order),
    CONSTRAINT trip_places_day_chk  CHECK (day_number >= 1),
    CONSTRAINT trip_places_sort_chk CHECK (sort_order >= 1)
);
CREATE INDEX trip_places_place_idx ON trip_places (place_id);

-- ============================================================
-- 지도 · 정원
-- ============================================================

CREATE TABLE region_visits (
    id              BIGSERIAL     PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    region_code     VARCHAR(10)   NOT NULL,
    lat             NUMERIC(10,7) NOT NULL,
    lng             NUMERIC(10,7) NOT NULL,
    accuracy_meters NUMERIC(6,1),
    verified_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT region_visits_user_fk   FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT region_visits_region_fk FOREIGN KEY (region_code) REFERENCES regions(code)
);
COMMENT ON TABLE region_visits IS '유니크 제약을 걸지 않는다 — 재방문해야 정원이 자란다 (ADR-038)';
CREATE INDEX region_visits_user_region_idx ON region_visits (user_id, region_code, verified_at DESC);

CREATE TABLE garden_objects (
    id          BIGSERIAL     PRIMARY KEY,
    region_code VARCHAR(10),
    name        VARCHAR(50) NOT NULL,
    type        VARCHAR(10) NOT NULL,
    max_stage   SMALLINT    NOT NULL,
    image_url   TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT garden_objects_region_fk FOREIGN KEY (region_code) REFERENCES regions(code),
    CONSTRAINT garden_objects_type_chk  CHECK (type IN ('PLANT','OBJECT')),
    CONSTRAINT garden_objects_stage_chk CHECK (max_stage >= 1)
);
COMMENT ON TABLE garden_objects IS '도감(마스터). region_code가 NULL이면 지역 공통';

CREATE TABLE user_garden_objects (
    id               BIGSERIAL     PRIMARY KEY,
    user_id          BIGINT      NOT NULL,
    garden_object_id BIGINT      NOT NULL,
    stage            SMALLINT    NOT NULL DEFAULT 1,
    position_x       SMALLINT,
    position_y       SMALLINT,
    unlocked_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_grown_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ugo_user_fk   FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ugo_object_fk FOREIGN KEY (garden_object_id) REFERENCES garden_objects(id),
    CONSTRAINT ugo_uk        UNIQUE (user_id, garden_object_id),
    CONSTRAINT ugo_stage_chk CHECK (stage >= 1)
);
COMMENT ON COLUMN user_garden_objects.last_grown_at IS '7일 쿨다운 판정 기준 (ADR-018)';

-- ============================================================
-- 타임캡슐
-- ============================================================

CREATE TABLE time_capsules (
    id               BIGSERIAL     PRIMARY KEY,
    user_id          BIGINT        NOT NULL,
    title            VARCHAR(60)   NOT NULL,
    content          TEXT          NOT NULL,
    unlock_type      VARCHAR(10)   NOT NULL,
    unlock_date      DATE,
    unlock_lat       NUMERIC(10,7),
    unlock_lng       NUMERIC(10,7),
    unlock_radius_m  INT,
    place_name       VARCHAR(60),
    status           VARCHAR(12)   NOT NULL DEFAULT 'SEALED',
    opened_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT time_capsules_user_fk    FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT time_capsules_type_chk   CHECK (unlock_type IN ('DATE','LOCATION')),
    CONSTRAINT time_capsules_status_chk CHECK (status IN ('SEALED','UNLOCKABLE','OPENED')),
    -- 해제 조건은 날짜와 위치 중 하나만 채운다 (TC-01)
    CONSTRAINT time_capsules_unlock_chk CHECK (
        (unlock_type = 'DATE'     AND unlock_date IS NOT NULL
                                  AND unlock_lat IS NULL AND unlock_lng IS NULL AND unlock_radius_m IS NULL)
     OR (unlock_type = 'LOCATION' AND unlock_date IS NULL
                                  AND unlock_lat IS NOT NULL AND unlock_lng IS NOT NULL AND unlock_radius_m IS NOT NULL)
    )
);
CREATE INDEX time_capsules_user_idx   ON time_capsules (user_id, created_at DESC);
CREATE INDEX time_capsules_sealed_idx ON time_capsules (status, unlock_date) WHERE status = 'SEALED';

CREATE TABLE time_capsule_media (
    capsule_id BIGINT   NOT NULL,
    media_id   BIGINT   NOT NULL,
    sort_order SMALLINT NOT NULL,
    PRIMARY KEY (capsule_id, media_id),
    CONSTRAINT tcm_capsule_fk FOREIGN KEY (capsule_id) REFERENCES time_capsules(id) ON DELETE CASCADE,
    CONSTRAINT tcm_media_fk   FOREIGN KEY (media_id)   REFERENCES media(id)
);

-- ============================================================
-- 아카이브
-- ============================================================

CREATE TABLE archives (
    id                   BIGSERIAL     PRIMARY KEY,
    owner_user_id        BIGINT      NOT NULL,
    trip_id              BIGINT,
    title                VARCHAR(60) NOT NULL,
    theme                VARCHAR(12) NOT NULL,
    primary_color        VARCHAR(7),
    cover_item_id        BIGINT,
    start_date           DATE,
    end_date             DATE,
    collaboration_status VARCHAR(10) NOT NULL DEFAULT 'NONE',
    origin_archive_id    BIGINT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT archives_owner_fk  FOREIGN KEY (owner_user_id) REFERENCES users(id),
    -- 1:1이되 각자 독립. 일정을 지워도 아카이브는 남고 연결만 끊긴다 (ADR-001)
    CONSTRAINT archives_trip_fk   FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE SET NULL,
    CONSTRAINT archives_trip_uk   UNIQUE (trip_id),
    CONSTRAINT archives_origin_fk FOREIGN KEY (origin_archive_id) REFERENCES archives(id) ON DELETE SET NULL,
    CONSTRAINT archives_theme_chk CHECK (theme IN ('BOOK','POLAROID','ALBUM','SCRAPBOOK')),
    CONSTRAINT archives_collab_chk CHECK (collaboration_status IN ('NONE','OPEN','CLOSED')),
    CONSTRAINT archives_color_chk CHECK (primary_color IS NULL OR primary_color ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT archives_date_chk  CHECK (start_date IS NULL OR end_date IS NULL OR start_date <= end_date)
);
COMMENT ON COLUMN archives.start_date IS '파생값 — 담긴 사진 taken_at의 최솟값 (ADR-030)';
CREATE INDEX archives_owner_idx ON archives (owner_user_id, created_at DESC);
CREATE INDEX archives_title_idx ON archives (title);
CREATE INDEX archives_date_idx  ON archives (start_date, end_date);

CREATE TABLE archive_items (
    id         BIGSERIAL     PRIMARY KEY,
    archive_id BIGINT       NOT NULL,
    media_id   BIGINT       NOT NULL,
    sort_order SMALLINT     NOT NULL,
    layout     JSONB        NOT NULL,
    caption    VARCHAR(300),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT archive_items_archive_fk FOREIGN KEY (archive_id) REFERENCES archives(id) ON DELETE CASCADE,
    CONSTRAINT archive_items_media_fk   FOREIGN KEY (media_id) REFERENCES media(id),
    CONSTRAINT archive_items_sort_chk   CHECK (sort_order >= 1)
);
COMMENT ON COLUMN archive_items.layout  IS '{x, y, width, height, rotation} — 캔버스 폭 기준 비율';
COMMENT ON COLUMN archive_items.caption IS '사진에 붙인 짧은 글 (ADR-029)';
CREATE INDEX archive_items_archive_idx ON archive_items (archive_id, sort_order);
CREATE INDEX archive_items_media_idx   ON archive_items (media_id);

-- archives와 archive_items가 서로를 참조하므로 대표 사진 FK는 뒤에 건다
ALTER TABLE archives
    ADD CONSTRAINT archives_cover_fk
    FOREIGN KEY (cover_item_id) REFERENCES archive_items(id) ON DELETE SET NULL;

CREATE TABLE archive_collaborators (
    id         BIGSERIAL     PRIMARY KEY,
    archive_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    role       VARCHAR(10) NOT NULL,
    status     VARCHAR(10) NOT NULL DEFAULT 'INVITED',
    invited_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    joined_at  TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ac_archive_fk FOREIGN KEY (archive_id) REFERENCES archives(id) ON DELETE CASCADE,
    CONSTRAINT ac_user_fk    FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ac_uk         UNIQUE (archive_id, user_id),
    CONSTRAINT ac_role_chk   CHECK (role IN ('OWNER','EDITOR')),
    CONSTRAINT ac_status_chk CHECK (status IN ('INVITED','JOINED','LEFT'))
);
CREATE INDEX ac_user_idx ON archive_collaborators (user_id, status);

-- ============================================================
-- 커뮤니티
-- ============================================================

CREATE TABLE posts (
    id            BIGSERIAL     PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    content       TEXT        NOT NULL,
    share_type    VARCHAR(10) NOT NULL,
    trip_id       BIGINT,
    archive_id    BIGINT,
    like_count    INT         NOT NULL DEFAULT 0,
    comment_count INT         NOT NULL DEFAULT 0,
    status        VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT posts_user_fk    FOREIGN KEY (user_id) REFERENCES users(id),
    -- 원본을 지워도 게시물은 남는다. 두 FK가 모두 NULL이 될 수 있으므로
    -- "최소 하나는 NOT NULL" CHECK를 걸지 않는다 (ADR-002)
    CONSTRAINT posts_trip_fk    FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE SET NULL,
    CONSTRAINT posts_archive_fk FOREIGN KEY (archive_id) REFERENCES archives(id) ON DELETE SET NULL,
    CONSTRAINT posts_share_chk  CHECK (share_type IN ('COURSE','ARCHIVE','BOTH')),
    CONSTRAINT posts_status_chk CHECK (status IN ('ACTIVE','DELETED'))
);
COMMENT ON COLUMN posts.share_type IS '등록 시점에 무엇을 공유했는지. 원본이 삭제된 뒤에도 남는다 (ADR-022)';
CREATE INDEX posts_user_idx    ON posts (user_id, created_at DESC);
CREATE INDEX posts_latest_idx  ON posts (status, created_at DESC);
CREATE INDEX posts_popular_idx ON posts (status, created_at, like_count DESC);

CREATE TABLE post_regions (
    post_id     BIGINT      NOT NULL,
    region_code VARCHAR(10) NOT NULL,
    source      VARCHAR(10) NOT NULL,
    PRIMARY KEY (post_id, region_code),
    CONSTRAINT post_regions_post_fk   FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT post_regions_region_fk FOREIGN KEY (region_code) REFERENCES regions(code),
    CONSTRAINT post_regions_src_chk   CHECK (source IN ('COURSE','MANUAL'))
);
COMMENT ON TABLE post_regions IS '등록 시점에 굳힌 지역 스냅샷. 원본 코스가 삭제돼도 남는다 (ADR-003)';
CREATE INDEX post_regions_region_idx ON post_regions (region_code, post_id);

CREATE TABLE post_likes (
    post_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT post_likes_post_fk FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT post_likes_user_fk FOREIGN KEY (user_id) REFERENCES users(id)
);
COMMENT ON TABLE post_likes IS '복합키가 중복 좋아요를 막는다 (ADR-006)';
CREATE INDEX post_likes_user_idx ON post_likes (user_id, created_at DESC);

CREATE TABLE comments (
    id                BIGSERIAL     PRIMARY KEY,
    post_id           BIGINT      NOT NULL,
    user_id           BIGINT      NOT NULL,
    parent_comment_id BIGINT,
    content           TEXT,
    status            VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT comments_post_fk   FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT comments_user_fk   FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT comments_parent_fk FOREIGN KEY (parent_comment_id) REFERENCES comments(id),
    CONSTRAINT comments_status_chk CHECK (status IN ('ACTIVE','DELETED')),
    -- 삭제된 댓글은 내용을 비운다. 대댓글이 달려 있으면 자리는 남는다 (ADR-007)
    CONSTRAINT comments_content_chk CHECK (status = 'DELETED' OR content IS NOT NULL)
);
CREATE INDEX comments_post_idx   ON comments (post_id, created_at);
CREATE INDEX comments_parent_idx ON comments (parent_comment_id, created_at);

-- ============================================================
-- 신고 · 제재
-- ============================================================

CREATE TABLE reports (
    id               BIGSERIAL     PRIMARY KEY,
    reporter_user_id BIGINT       NOT NULL,
    target_type      VARCHAR(10)  NOT NULL,
    target_id        BIGINT       NOT NULL,
    target_user_id   BIGINT       NOT NULL,
    reason           VARCHAR(12)  NOT NULL,
    detail           VARCHAR(200),
    status           VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    reviewed_by      BIGINT,
    reviewed_at      TIMESTAMPTZ,
    note             VARCHAR(200),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT reports_reporter_fk FOREIGN KEY (reporter_user_id) REFERENCES users(id),
    CONSTRAINT reports_target_fk   FOREIGN KEY (target_user_id) REFERENCES users(id),
    CONSTRAINT reports_admin_fk    FOREIGN KEY (reviewed_by) REFERENCES admins(id),
    -- 같은 대상을 반복 신고할 수 없다 (ADR-006)
    CONSTRAINT reports_uk          UNIQUE (reporter_user_id, target_type, target_id),
    CONSTRAINT reports_target_chk  CHECK (target_type IN ('POST','COMMENT')),
    CONSTRAINT reports_reason_chk  CHECK (reason IN ('OBSCENE','ABUSE','SPAM','FALSE_INFO','ETC')),
    CONSTRAINT reports_status_chk  CHECK (status IN ('PENDING','VALID','REJECTED')),
    -- ETC이면 설명이 필수. "기타"만으로는 판정할 수 없다 (ADR-046)
    CONSTRAINT reports_detail_chk  CHECK (reason <> 'ETC' OR detail IS NOT NULL)
);
COMMENT ON COLUMN reports.target_id IS '다형 참조라 FK를 걸지 않는다';
CREATE INDEX reports_pending_idx ON reports (status, created_at);
CREATE INDEX reports_target_idx  ON reports (target_user_id, status);

CREATE TABLE sanctions (
    id         BIGSERIAL     PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(10)  NOT NULL,
    source     VARCHAR(10)  NOT NULL,
    reason     VARCHAR(200) NOT NULL,
    issued_by  BIGINT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT sanctions_user_fk   FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT sanctions_admin_fk  FOREIGN KEY (issued_by) REFERENCES admins(id),
    CONSTRAINT sanctions_type_chk  CHECK (type IN ('WARNING','BLOCK','UNBLOCK')),
    CONSTRAINT sanctions_src_chk   CHECK (source IN ('MANUAL','AUTO')),
    -- 자동 제재는 관리자가 없다 (ADMIN-10)
    CONSTRAINT sanctions_issuer_chk CHECK (source = 'AUTO' OR issued_by IS NOT NULL)
);
COMMENT ON TABLE sanctions IS '만료 컬럼이 없다 — 차단은 무기한이고 관리자가 푼다 (ADR-033)';
CREATE INDEX sanctions_user_idx ON sanctions (user_id, created_at DESC);

-- ============================================================
-- 알림
-- ============================================================

CREATE TABLE notifications (
    id          BIGSERIAL     PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    title       VARCHAR(100) NOT NULL,
    body        TEXT,
    target_type VARCHAR(20),
    target_id   BIGINT,
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT notifications_user_fk FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT notifications_type_chk CHECK (type IN ('COLLAB_INVITE','CAPSULE_UNLOCK','WARNING')),
    CONSTRAINT notifications_target_chk
        CHECK (target_type IS NULL OR target_type IN ('ARCHIVE','TIME_CAPSULE','SANCTION'))
);
COMMENT ON TABLE notifications IS '차단은 알림으로 보내지 않는다 — 차단된 사용자는 앱에 들어올 수 없다 (AUTH-06)';
CREATE INDEX notifications_user_idx   ON notifications (user_id, created_at DESC);
CREATE INDEX notifications_unread_idx ON notifications (user_id) WHERE is_read = FALSE;

CREATE TABLE notification_settings (
    user_id BIGINT      NOT NULL,
    type    VARCHAR(20) NOT NULL,
    enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id, type),
    CONSTRAINT ns_user_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ns_type_chk CHECK (type IN ('COLLAB_INVITE','CAPSULE_UNLOCK','WARNING')),
    -- 경고 알림은 끌 수 없다 (ADR-047)
    CONSTRAINT ns_warning_chk CHECK (type <> 'WARNING' OR enabled = TRUE)
);
