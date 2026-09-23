ALTER TABLE user_garden_objects ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN user_garden_objects.version IS
    '낙관적 락(JPA @Version). 같은 오브젝트를 동시에 두 번 성장시키는 경합을 막는다';
