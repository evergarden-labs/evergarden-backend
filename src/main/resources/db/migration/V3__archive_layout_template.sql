-- 공유 게시물에서 아카이브를 "틀만" 가져올 때(ARCH-16) 쓸 빈 자리 안내.
-- ArchiveItem은 media가 NOT NULL이라 사진 없는 자리를 못 만들어서, 배치·순서만
-- Archive 쪽에 따로 둔다(ADR-057).

ALTER TABLE archives
    ADD COLUMN layout_template JSONB;

COMMENT ON COLUMN archives.layout_template IS
    '가져온 아카이브의 빈 자리 안내. [{sortOrder, layout}] 배열. 그 외엔 NULL (ADR-057)';
