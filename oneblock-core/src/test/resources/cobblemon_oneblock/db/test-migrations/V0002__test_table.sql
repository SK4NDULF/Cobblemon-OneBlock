-- Test-only migration used to exercise the backup-before-migrate path.
CREATE TABLE test_v2 (
    id    INTEGER PRIMARY KEY,
    value TEXT NOT NULL
) STRICT;
