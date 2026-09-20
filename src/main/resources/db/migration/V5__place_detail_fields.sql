ALTER TABLE places ADD COLUMN image_urls TEXT;
ALTER TABLE places ADD COLUMN detail_synced_at TIMESTAMPTZ;

COMMENT ON COLUMN places.image_urls IS
    '관광사진정보조회(detailImage2) 결과를 JSON 배열 문자열로 저장한다';
COMMENT ON COLUMN places.detail_synced_at IS
    'getPlace 상세조회로 overview·use_time·rest_date·image_urls를 채운 시각. null이면 아직 한 번도 안 채웠다는 뜻이다';
