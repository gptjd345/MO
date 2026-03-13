--liquibase formatted sql

--changeset dev:add_token_version_to_users
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
--rollback ALTER TABLE users DROP COLUMN token_version;

--changeset dev:create_refresh_tokens
CREATE TABLE refresh_tokens (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    jti        VARCHAR(36)  NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    revoked_at TIMESTAMP,
    CONSTRAINT uk_refresh_tokens_jti UNIQUE (jti)
);
--rollback DROP TABLE refresh_tokens;
