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
