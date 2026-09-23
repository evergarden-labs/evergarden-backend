ALTER TABLE region_visits ADD COLUMN reward_status VARCHAR(10) NOT NULL DEFAULT 'NONE';
ALTER TABLE region_visits ADD COLUMN garden_object_id BIGINT;
ALTER TABLE region_visits ADD COLUMN previous_stage SMALLINT;
ALTER TABLE region_visits ADD COLUMN current_stage SMALLINT;
ALTER TABLE region_visits ADD COLUMN next_available_at TIMESTAMPTZ;

ALTER TABLE region_visits ADD CONSTRAINT region_visits_garden_object_fk
    FOREIGN KEY (garden_object_id) REFERENCES garden_objects(id);
ALTER TABLE region_visits ADD CONSTRAINT region_visits_reward_status_chk
    CHECK (reward_status IN ('UNLOCKED','GROWN','COOLDOWN','NONE'));

COMMENT ON COLUMN region_visits.reward_status IS
    '인증 시점에 계산해 둔 보상 결과 스냅샷(GARDEN-02). 이후 재방문으로 정원 상태가 바뀌어도 이 값은 그대로다';
COMMENT ON COLUMN region_visits.garden_object_id IS
    '그 인증으로 해금·성장한 오브젝트. rewardStatus가 NONE이면 NULL';
COMMENT ON COLUMN region_visits.previous_stage IS '성장 전 단계. UNLOCKED·NONE이면 NULL';
COMMENT ON COLUMN region_visits.current_stage IS '성장 후(또는 해금 직후) 단계. NONE이면 NULL';
COMMENT ON COLUMN region_visits.next_available_at IS
    'COOLDOWN일 때 다음 성장 가능 시각(ADR-018). 그 외에는 NULL';
