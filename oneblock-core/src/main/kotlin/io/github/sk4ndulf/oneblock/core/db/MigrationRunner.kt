package io.github.sk4ndulf.oneblock.core.db

import org.slf4j.Logger

/**
 * Versioned schema migrations, applied in order inside a transaction per migration.
 * New migrations are appended to [migrations] — never edit an already-shipped migration.
 */
class MigrationRunner(private val database: Database, private val logger: Logger) {

    private data class Migration(
        val version: Int,
        val description: String,
        val statements: (SqlDialect) -> List<String>,
    )

    private val migrations = listOf(
        Migration(1, "players table (identity + last_seen for the inactivity purge)") { _ ->
            listOf(
                """
                CREATE TABLE IF NOT EXISTS players (
                    uuid       CHAR(36)    NOT NULL PRIMARY KEY,
                    name       VARCHAR(16) NOT NULL,
                    first_seen BIGINT      NOT NULL,
                    last_seen  BIGINT      NOT NULL
                )
                """.trimIndent(),
            )
        },
        Migration(2, "meta key/value table (world-bound state like hub_platform_generated)") { _ ->
            listOf(
                """
                CREATE TABLE IF NOT EXISTS meta (
                    meta_key   VARCHAR(64) NOT NULL PRIMARY KEY,
                    meta_value TEXT        NOT NULL
                )
                """.trimIndent(),
            )
        },
        Migration(3, "islands table (grid slots, state machine ACTIVE/ARCHIVED/PURGED)") { dialect ->
            val idColumn = when (dialect) {
                SqlDialect.SQLITE -> "id INTEGER PRIMARY KEY AUTOINCREMENT"
                SqlDialect.MYSQL -> "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY"
            }
            listOf(
                """
                CREATE TABLE IF NOT EXISTS islands (
                    $idColumn,
                    slot         INT         NOT NULL,
                    owner_uuid   CHAR(36)    NOT NULL,
                    state        VARCHAR(16) NOT NULL,
                    border_level INT         NOT NULL,
                    break_count  BIGINT      NOT NULL,
                    created_at   BIGINT      NOT NULL,
                    archived_at  BIGINT
                )
                """.trimIndent(),
                "CREATE INDEX idx_islands_owner ON islands(owner_uuid)",
                "CREATE INDEX idx_islands_slot ON islands(slot)",
            )
        },
        Migration(4, "island_members table (party/co-op membership)") { _ ->
            listOf(
                """
                CREATE TABLE IF NOT EXISTS island_members (
                    island_id   BIGINT   NOT NULL,
                    member_uuid CHAR(36) NOT NULL,
                    added_at    BIGINT   NOT NULL,
                    PRIMARY KEY (island_id, member_uuid)
                )
                """.trimIndent(),
                "CREATE INDEX idx_members_uuid ON island_members(member_uuid)",
            )
        },
    )

    fun run() {
        database.sync { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INT NOT NULL PRIMARY KEY)")
            }
        }
        val current = database.schemaVersion()
        val pending = migrations.filter { it.version > current }.sortedBy { it.version }
        if (pending.isEmpty()) return

        for (migration in pending) {
            logger.info("Applying database migration {}: {}", migration.version, migration.description)
            database.sync { connection ->
                connection.autoCommit = false
                try {
                    connection.createStatement().use { statement ->
                        for (sql in migration.statements(database.dialect)) {
                            statement.execute(sql)
                        }
                    }
                    connection.prepareStatement("INSERT INTO schema_version (version) VALUES (?)").use {
                        it.setInt(1, migration.version)
                        it.executeUpdate()
                    }
                    connection.commit()
                } catch (e: Exception) {
                    connection.rollback()
                    throw IllegalStateException("Migration ${migration.version} failed: ${e.message}", e)
                } finally {
                    connection.autoCommit = true
                }
            }
        }
    }
}
