--liquibase formatted sql

--changeset dev:add_stats_tables
CREATE TABLE daily_stats (
    id              BIGSERIAL PRIMARY KEY,
    user_id         INTEGER NOT NULL REFERENCES users(id),
    stat_date       DATE NOT NULL,
    completed_count INTEGER NOT NULL DEFAULT 0,
    scheduled_count INTEGER NOT NULL DEFAULT 0,
    created_count   INTEGER NOT NULL DEFAULT 0,
    completion_rate FLOAT   NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, stat_date)
);

CREATE TABLE weekly_stats (
    id              BIGSERIAL PRIMARY KEY,
    user_id         INTEGER NOT NULL REFERENCES users(id),
    year            INTEGER NOT NULL,
    week_number     INTEGER NOT NULL,
    completed_count INTEGER NOT NULL DEFAULT 0,
    scheduled_count INTEGER NOT NULL DEFAULT 0,
    created_count   INTEGER NOT NULL DEFAULT 0,
    completion_rate FLOAT   NOT NULL DEFAULT 0,
    goal_achieved   BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, year, week_number)
);

CREATE TABLE streak_stats (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             INTEGER NOT NULL REFERENCES users(id) UNIQUE,
    current_streak      INTEGER NOT NULL DEFAULT 0,
    longest_streak      INTEGER NOT NULL DEFAULT 0,
    last_week_achieved  INTEGER,
    last_year_achieved  INTEGER,
    is_freezed          BOOLEAN NOT NULL DEFAULT FALSE,
    freeze_week         INTEGER,
    freeze_year         INTEGER,
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE todo_events (
    id          BIGSERIAL PRIMARY KEY,
    user_id     INTEGER NOT NULL REFERENCES users(id),
    todo_id     INTEGER NOT NULL,
    event_type  VARCHAR(20) NOT NULL,
    event_date  DATE NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_todo_events_created_at ON todo_events(created_at);
CREATE INDEX idx_todo_events_user_event_date ON todo_events(user_id, event_date);

CREATE TABLE weekly_goals (
    id          BIGSERIAL PRIMARY KEY,
    user_id     INTEGER NOT NULL REFERENCES users(id),
    year        INTEGER NOT NULL,
    week_number INTEGER NOT NULL,
    goal_count  INTEGER NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, year, week_number)
);
