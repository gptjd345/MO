--liquibase formatted sql

-- ================================================================
-- [2-1] app_users 에 nickname 컬럼 추가
-- precondition: 컬럼이 없을 때만 실행, 있으면 MARK_RAN
-- ================================================================
--changeset dev:add_nickname_column
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='app_users' AND column_name='nickname'
ALTER TABLE app_users
    ADD COLUMN nickname VARCHAR(50) NOT NULL DEFAULT '나무늘보';
--rollback ALTER TABLE app_users DROP COLUMN nickname;

-- ================================================================
-- [2-2] 기존 유저에게 순번 기반 닉네임 부여
-- (나무늘보1, 나무늘보2, ... — id 오름차순)
-- splitStatements:false: PL/pgSQL 블록 내 세미콜론 분리 방지
-- ================================================================
--changeset dev:init_nickname_values splitStatements:false
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM app_users WHERE nickname != '나무늘보'
DO $$
DECLARE
    r   RECORD;
    seq INT := 1;
BEGIN
    FOR r IN
        SELECT id FROM app_users WHERE nickname = '나무늘보' ORDER BY id ASC
    LOOP
        UPDATE app_users SET nickname = '나무늘보' || seq WHERE id = r.id;
        seq := seq + 1;
    END LOOP;
END $$;
--rollback SELECT 1; -- 데이터 롤백은 수동으로 처리
