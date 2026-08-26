package io.github.sk4ndulf.cobblemon.oneblock.core.config

/**
 * Storage configuration. SQLite is the zero-config default; MySQL/MariaDB is opt-in
 * for larger networks. Only ever configured through the config file, never in-game.
 */
data class DatabaseConfig(
    /** "sqlite" (default, embedded, no setup) or "mysql" (also covers MariaDB). */
    val storage: String = "sqlite",

    val mysqlHost: String = "localhost",
    val mysqlPort: Int = 3306,
    val mysqlDatabase: String = "cobblemon_oneblock",
    val mysqlUser: String = "cobblemon_oneblock",
    val mysqlPassword: String = "",

    /** Connection pool size. Ignored for SQLite (which is fixed to a single connection). */
    val poolSize: Int = 8,
) {
    val isMysql: Boolean
        get() = storage.equals("mysql", ignoreCase = true) || storage.equals("mariadb", ignoreCase = true)
}
