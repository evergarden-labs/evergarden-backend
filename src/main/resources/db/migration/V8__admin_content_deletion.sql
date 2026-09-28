ALTER TABLE posts ADD COLUMN deleted_by_admin_id BIGINT REFERENCES admins(id);
ALTER TABLE posts ADD COLUMN delete_reason VARCHAR(200);

ALTER TABLE comments ADD COLUMN deleted_by_admin_id BIGINT REFERENCES admins(id);
ALTER TABLE comments ADD COLUMN delete_reason VARCHAR(200);

COMMENT ON COLUMN posts.deleted_by_admin_id IS
    '관리자가 강제 삭제했으면 그 관리자(ADMIN-06). 작성자 본인 삭제(COMM-06)면 NULL';
COMMENT ON COLUMN posts.delete_reason IS
    '관리자 강제 삭제 사유(ADMIN-06). 작성자 본인 삭제면 NULL';
COMMENT ON COLUMN comments.deleted_by_admin_id IS
    '관리자가 강제 삭제했으면 그 관리자(ADMIN-07·08 — 대댓글도 같은 테이블). 작성자 본인 삭제(COMM-13·16)면 NULL';
COMMENT ON COLUMN comments.delete_reason IS
    '관리자 강제 삭제 사유(ADMIN-07·08). 작성자 본인 삭제면 NULL';
