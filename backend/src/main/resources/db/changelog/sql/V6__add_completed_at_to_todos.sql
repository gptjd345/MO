--liquibase formatted sql

--changeset dev:add_completed_at_to_todos
ALTER TABLE todos ADD COLUMN completed_at DATE;
