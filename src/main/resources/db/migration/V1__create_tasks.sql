-- V1__create_tasks.sql
-- Creates the tasks table with all constraints and supporting objects.

-- Custom types for status and priority (check constraints used instead for portability/Flyway safety)
CREATE TABLE tasks (
    id            BIGSERIAL PRIMARY KEY,
    title         TEXT        NOT NULL,
    description   TEXT,
    status        TEXT        NOT NULL DEFAULT 'TODO',
    priority      TEXT        NOT NULL DEFAULT 'MEDIUM',
    due_date      DATE,
    completed_at  TIMESTAMPTZ,
    tags          TEXT[]      NOT NULL DEFAULT '{}',
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    search_vector TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(description, ''))
    ) STORED,

    CONSTRAINT ck_tasks_title_not_blank
        CHECK (trim(title) <> ''),

    CONSTRAINT ck_tasks_status
        CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED')),

    CONSTRAINT ck_tasks_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),

    CONSTRAINT ck_tasks_completed_at_consistent
        CHECK (
            (status = 'DONE' AND completed_at IS NOT NULL)
            OR (status <> 'DONE' AND completed_at IS NULL)
        ),

    CONSTRAINT ck_tasks_tags_limit
        CHECK (array_length(tags, 1) IS NULL OR array_length(tags, 1) <= 10)
);

-- Index for full-text search
CREATE INDEX idx_tasks_search_vector ON tasks USING GIN (search_vector);

-- Index for status filtering
CREATE INDEX idx_tasks_status ON tasks (status);

-- Index for due_date filtering
CREATE INDEX idx_tasks_due_date ON tasks (due_date);

-- Trigger function to keep updated_at current
CREATE OR REPLACE FUNCTION tasks_set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_tasks_updated_at
    BEFORE UPDATE ON tasks
    FOR EACH ROW EXECUTE FUNCTION tasks_set_updated_at();
