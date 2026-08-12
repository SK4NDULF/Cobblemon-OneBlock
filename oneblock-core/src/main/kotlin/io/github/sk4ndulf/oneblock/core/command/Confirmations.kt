package io.github.sk4ndulf.oneblock.core.command

import java.util.UUID

/**
 * Two-step confirmation for destructive commands (/ob reset, /ob delete).
 * A pending confirmation expires after 30 seconds. Server-thread only.
 */
object Confirmations {

    private const val TIMEOUT_MILLIS = 30_000L

    private data class Pending(val action: String, val expiresAt: Long)

    private val pending = HashMap<UUID, Pending>()

    fun request(player: UUID, action: String) {
        pending[player] = Pending(action, System.currentTimeMillis() + TIMEOUT_MILLIS)
    }

    /** Consumes a matching, unexpired confirmation. Returns false if there is none. */
    fun consume(player: UUID, action: String): Boolean {
        val entry = pending.remove(player) ?: return false
        return entry.action == action && entry.expiresAt >= System.currentTimeMillis()
    }
}
