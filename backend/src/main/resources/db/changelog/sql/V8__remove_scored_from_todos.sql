--liquibase formatted sql

--changeset dev:remove_scored_from_todos
ALTER TABLE todos DROP COLUMN scored;
