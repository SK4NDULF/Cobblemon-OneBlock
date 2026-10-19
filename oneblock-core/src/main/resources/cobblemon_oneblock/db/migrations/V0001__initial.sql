-- V0001: world binding, durable operation journal and audit log.
-- Never edit an applied migration: its SHA-256 is stored in schema_migrations and checked on every start.

CREATE TABLE world_meta (
    key   TEXT PRIMARY KEY NOT NULL,
    value TEXT NOT NULL
) STRICT;

CREATE TABLE operations (
    operation_id TEXT PRIMARY KEY NOT NULL,
    request_id   TEXT NOT NULL UNIQUE,
    type         TEXT NOT NULL,
    island_id    TEXT,
    actor        TEXT NOT NULL,
    stage        TEXT NOT NULL,
    status       TEXT NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'ABORTED', 'RECOVERY_REQUIRED')),
    revision     INTEGER NOT NULL CHECK (revision >= 0),
    intent       TEXT NOT NULL,
    result       TEXT,
    error        TEXT,
    created_at   INTEGER NOT NULL,
    updated_at   INTEGER NOT NULL
) STRICT;

CREATE INDEX operations_open ON operations (status) WHERE status IN ('PENDING', 'RECOVERY_REQUIRED');

CREATE TABLE audit_log (
    audit_id     INTEGER PRIMARY KEY,
    at           INTEGER NOT NULL,
    actor        TEXT NOT NULL,
    action       TEXT NOT NULL,
    target       TEXT,
    operation_id TEXT REFERENCES operations (operation_id),
    outcome      TEXT NOT NULL CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILED', 'PENDING')),
    reason       TEXT,
    details      TEXT
) STRICT;

CREATE INDEX audit_log_at ON audit_log (at);
