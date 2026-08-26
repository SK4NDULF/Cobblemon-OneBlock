package io.github.sk4ndulf.cobblemon.oneblock.core.db

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
        Migration(5, "islands.points column (border-level progression)") { _ ->
            listOf(
                "ALTER TABLE islands ADD COLUMN points DOUBLE NOT NULL DEFAULT 0",
            )
        },
        Migration(6, "island buffs + visitor Cobblemon permissions") { _ ->
            listOf(
                """
                CREATE TABLE IF NOT EXISTS island_buffs (
                    island_id  BIGINT      NOT NULL,
                    buff_type  VARCHAR(32) NOT NULL,
                    value      DOUBLE      NOT NULL,
                    expires_at BIGINT      NOT NULL,
                    PRIMARY KEY (island_id, buff_type)
                )
                """.trimIndent(),
                "ALTER TABLE islands ADD COLUMN allow_visitor_catch INT NOT NULL DEFAULT 0",
                "ALTER TABLE islands ADD COLUMN allow_visitor_battle INT NOT NULL DEFAULT 0",
            )
        },
        Migration(7, "island_biome_regions table (biome editor)") { dialect ->
            val idColumn = when (dialect) {
                SqlDialect.SQLITE -> "id INTEGER PRIMARY KEY AUTOINCREMENT"
                SqlDialect.MYSQL -> "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY"
            }
            listOf(
                """
                CREATE TABLE IF NOT EXISTS island_biome_regions (
                    $idColumn,
                    island_id BIGINT       NOT NULL,
                    min_x     INT          NOT NULL,
                    min_y     INT          NOT NULL,
                    min_z     INT          NOT NULL,
                    max_x     INT          NOT NULL,
                    max_y     INT          NOT NULL,
                    max_z     INT          NOT NULL,
                    biome     VARCHAR(128) NOT NULL
                )
                """.trimIndent(),
                "CREATE INDEX idx_biome_regions_island ON island_biome_regions(island_id)",
            )
        },
        Migration(8, "island_bans table (per-island visitor bans)") { _ ->
            listOf(
                """
                CREATE TABLE IF NOT EXISTS island_bans (
                    island_id   BIGINT       NOT NULL,
                    banned_uuid CHAR(36)     NOT NULL,
                    banned_by   CHAR(36),
                    reason      VARCHAR(256) NOT NULL,
                    created_at  BIGINT       NOT NULL,
                    PRIMARY KEY (island_id, banned_uuid)
                )
                """.trimIndent(),
            )
        },
        Migration(9, "island tech tree: unlocks, point claims, balances, spend delegation") { _ ->
            listOf(
                """
                CREATE TABLE IF NOT EXISTS island_tech (
                    island_id   BIGINT      NOT NULL,
                    node_id     VARCHAR(64) NOT NULL,
                    level       INT         NOT NULL,
                    unlocked_at BIGINT      NOT NULL,
                    PRIMARY KEY (island_id, node_id)
                )
                """.trimIndent(),
                // The primary key here is load-bearing, not just an index: it is what stops
                // two members of the same island banking the same source in the same tick.
                // See TechRepository.claimSync.
                """
                CREATE TABLE IF NOT EXISTS island_claims (
                    island_id  BIGINT       NOT NULL,
                    source_id  VARCHAR(128) NOT NULL,
                    claimed_by CHAR(36)     NOT NULL,
                    claimed_at BIGINT       NOT NULL,
                    PRIMARY KEY (island_id, source_id)
                )
                """.trimIndent(),
                // Two counters: the spendable balance, and a lifetime total that never drops,
                // so a `tech_points>=N` gate cannot close again when the island spends.
                "ALTER TABLE islands ADD COLUMN tech_points BIGINT NOT NULL DEFAULT 0",
                "ALTER TABLE islands ADD COLUMN tech_points_earned BIGINT NOT NULL DEFAULT 0",
                "ALTER TABLE island_members ADD COLUMN may_spend_tech INT NOT NULL DEFAULT 0",
            )
        },
        Migration(10, "islands.name column (owner-chosen island name)") { _ ->
            // Nullable: an island with no name falls back to its owner's name at display time,
            // so there is nothing to backfill and no default that would be wrong later.
            listOf(
                "ALTER TABLE islands ADD COLUMN name VARCHAR(48)",
            )
        },
        Migration(11, "drop the tech tree tables (the system was removed, see WORKLOG)") { _ ->
            // Migrations are append-only, so 9 stays exactly as it shipped and this one undoes
            // it. Dropping the tables is safe: nothing reads them any more and the data has no
            // meaning without the tree.
            //
            // The three columns migration 9 added to existing tables — islands.tech_points,
            // islands.tech_points_earned and island_members.may_spend_tech — are deliberately
            // NOT dropped. Dropping a column behaves differently across SQLite and MariaDB and
            // is the classic way a migration bricks a server's startup, and three unused
            // columns cost nothing. The quest system will either reuse them or a later
            // migration will clear them once there is a reason to touch that table anyway.
            listOf(
                "DROP TABLE IF EXISTS island_tech",
                "DROP TABLE IF EXISTS island_claims",
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
