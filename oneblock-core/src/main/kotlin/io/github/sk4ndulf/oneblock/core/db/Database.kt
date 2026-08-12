package io.github.sk4ndulf.oneblock.core.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.sk4ndulf.oneblock.core.config.DatabaseConfig
import org.slf4j.Logger
import java.nio.file.Path
import java.sql.Connection
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

enum class SqlDialect { SQLITE, MYSQL }

/**
 * Connection pool + async access wrapper.
 *
 * All database work runs on a dedicated executor via [async] — never on the server thread.
 * Callers hop back to the server thread themselves (e.g. `server.execute { ... }`) before
 * touching game state with the result.
 */
class Database private constructor(
    private val pool: HikariDataSource,
    val dialect: SqlDialect,
    private val logger: Logger,
) : AutoCloseable {

    companion object {
        fun connect(config: DatabaseConfig, configDir: Path, logger: Logger): Database {
            val hikari = HikariConfig()
            val dialect: SqlDialect
            if (config.isMysql) {
                dialect = SqlDialect.MYSQL
                hikari.jdbcUrl = "jdbc:mariadb://${config.mysqlHost}:${config.mysqlPort}/${config.mysqlDatabase}"
                hikari.username = config.mysqlUser
                hikari.password = config.mysqlPassword
                hikari.maximumPoolSize = config.poolSize.coerceIn(1, 32)
            } else {
                dialect = SqlDialect.SQLITE
                hikari.jdbcUrl = "jdbc:sqlite:${configDir.resolve("data.db").toAbsolutePath()}"
                // SQLite allows only one writer; a bigger pool just causes lock contention.
                hikari.maximumPoolSize = 1
            }
            hikari.poolName = "oneblock-db"
            hikari.connectionTimeout = TimeUnit.SECONDS.toMillis(10)
            val pool = HikariDataSource(hikari)
            return Database(pool, dialect, logger)
        }
    }

    private val executor: ExecutorService = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "oneblock-db-worker").apply { isDaemon = true }
    }

    fun <T> async(block: (Connection) -> T): CompletableFuture<T> =
        CompletableFuture.supplyAsync({ pool.connection.use(block) }, executor)
            .whenComplete { _, error ->
                if (error != null) {
                    logger.error("Async database operation failed", error)
                }
            }

    /** Synchronous access — only for startup/shutdown paths (migrations, connection test). */
    fun <T> sync(block: (Connection) -> T): T = pool.connection.use(block)

    fun runMigrations() {
        MigrationRunner(this, logger).run()
    }

    fun schemaVersion(): Int = sync { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT MAX(version) FROM schema_version").use { result ->
                if (result.next()) result.getInt(1) else 0
            }
        }
    }

    /** Cheap connectivity check for `/ob reload`. Returns null on success, else the error message. */
    fun testConnection(): String? = try {
        sync { connection -> connection.createStatement().use { it.execute("SELECT 1") } }
        null
    } catch (e: Exception) {
        e.message ?: e.javaClass.simpleName
    }

    override fun close() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("Database executor did not drain within 10s; pending writes may be lost.")
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        pool.close()
    }
}
