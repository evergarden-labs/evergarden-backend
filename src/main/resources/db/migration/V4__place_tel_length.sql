-- TourAPI tel 필드는 자유 텍스트에 가까워(대표번호·예약번호 병기, 안내문구 등)
-- VARCHAR(50)로는 부족한 실제 데이터가 나왔다(전국 관광지 초기 시딩 중 확인).
-- 주소·개요처럼 예측 못 할 길이의 외부 원문이라 TEXT로 바꾼다.
ALTER TABLE places ALTER COLUMN tel TYPE TEXT;
