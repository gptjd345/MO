--liquibase formatted sql

-- ================================================================
-- [1-1] app_users 테이블 생성
-- precondition: 테이블이 없을 때만 실행, 있으면 MARK_RAN
-- ================================================================
--changeset dev:create_app_users
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='app_users'
CREATE TABLE app_users (
    id       BIGSERIAL    PRIMARY KEY,
    email    VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    plan     VARCHAR(255) NOT NULL DEFAULT 'FREE',
    score    INT          NOT NULL DEFAULT 0
);
--rollback DROP TABLE app_users;

-- ================================================================
-- [1-2] todos 테이블 생성
-- ================================================================
--changeset dev:create_todos
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='todos'
CREATE TABLE todos (
    id           BIGSERIAL    PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    content      TEXT,
    is_completed BOOLEAN      NOT NULL DEFAULT FALSE,
    start_date   DATE,
    end_date     DATE,
    priority     VARCHAR(255) NOT NULL DEFAULT 'MEDIUM',
    user_id      BIGINT       NOT NULL,
    scored       BOOLEAN      NOT NULL DEFAULT FALSE
);
--rollback DROP TABLE todos;

-- ================================================================
-- [1-3] payments 테이블 생성
-- ================================================================
--changeset dev:create_payments
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='payments'
CREATE TABLE payments (
    id                BIGSERIAL    PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    amount            BIGINT       NOT NULL,
    status            VARCHAR(255) NOT NULL DEFAULT 'INIT',
    idempotency_key   VARCHAR(255) NOT NULL,
    pg_transaction_id VARCHAR(255),
    fail_reason       VARCHAR(255),
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP,
    CONSTRAINT uk_payments_user_idempotency UNIQUE (user_id, idempotency_key)
);
--rollback DROP TABLE payments;
