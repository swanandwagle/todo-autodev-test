CREATE TABLE tasks (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    title          VARCHAR(200)  NOT NULL,
    description    TEXT          NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'TODO',
    priority       VARCHAR(10)   NOT NULL DEFAULT 'MEDIUM',
    due_date       DATE          NULL,
    tags           TEXT[]        NOT NULL DEFAULT '{}',
    completed_at   TIMESTAMPTZ   NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    search_vector  TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(description, '')), 'B')
    ) STORED,
    CONSTRAINT pk_tasks PRIMARY KEY (id),
    CONSTRAINT ck_tasks_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_tasks_status CHECK (status IN ('TODO','IN_PROGRESS','DONE','CANCELLED')),
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
    CONSTRAINT ck_tasks_completed_at_consistent CHECK ((status = 'DONE') = (completed_at IS NOT NULL)),
    CONSTRAINT ck_tasks_tags_limit CHECK (cardinality(tags) <= 10)
);

CREATE INDEX ix_tasks_status_due_date ON tasks (status, due_date);
CREATE INDEX ix_tasks_open_due_date ON tasks (due_date) WHERE status IN ('TODO','IN_PROGRESS');
CREATE INDEX ix_tasks_priority ON tasks (priority);
CREATE INDEX ix_tasks_created_at ON tasks (created_at DESC);
CREATE INDEX ix_tasks_updated_at ON tasks (updated_at DESC);
CREATE INDEX ix_tasks_tags_gin ON tasks USING GIN (tags);
CREATE INDEX ix_tasks_search_gin ON tasks USING GIN (search_vector);

CREATE OR REPLACE FUNCTION trg_set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tasks_set_updated_at
    BEFORE UPDATE ON tasks
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
