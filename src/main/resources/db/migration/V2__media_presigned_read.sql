-- 미디어 조회를 presigned GET으로 바꾼다.
--
-- url·thumbnail_url을 고정값으로 저장하지 않는다. presigned URL은 발급 순간부터
-- 만료 카운트다운이 시작돼 DB에 저장해봐야 곧 못 쓰게 된다. 대신 S3 키만 저장해 두고
-- 조회 시점에 서비스가 그때그때 presigned GET을 발급한다.

ALTER TABLE media
    DROP COLUMN url,
    DROP COLUMN thumbnail_url,
    ADD COLUMN thumbnail_key TEXT;

COMMENT ON COLUMN media.thumbnail_key IS '사진 리사이즈본의 S3 키. 조회 시 presigned GET으로 URL을 만든다. 영상은 항상 NULL';
