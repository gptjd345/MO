--liquibase formatted sql

--changeset dev:add_ranking_version_to_users
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name='ranking_version'
ALTER TABLE users ADD COLUMN ranking_version INTEGER NOT NULL DEFAULT 0;
--rollback ALTER TABLE users DROP COLUMN ranking_version;
