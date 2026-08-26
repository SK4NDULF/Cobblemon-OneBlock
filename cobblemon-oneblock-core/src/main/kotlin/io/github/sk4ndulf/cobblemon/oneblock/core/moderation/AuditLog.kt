package io.github.sk4ndulf.cobblemon.oneblock.core.moderation

import io.github.sk4ndulf.cobblemon.oneblock.api.event.AuditEvent
import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Moderation audit trail. Every entry is written as one JSON line to
 * `config/cobblemon_oneblock/audit.log`, published on the API event bus, and — if configured —
 * pushed to a Discord webhook.
 *
 * File and network work happen off the server thread; a failing webhook never stalls
 * or crashes the server.
 */
object AuditLog {

    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "cobblemon-oneblock-audit").apply { isDaemon = true }
    }

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }

    private val file: Path get() = OneBlockCore.configManager.configDir.resolve("audit.log")

    /** Records an action. Call on the server thread; I/O is dispatched asynchronously. */
    fun record(action: String, actor: ServerPlayer?, target: UUID?, details: String) {
        record(action, actor?.uuid, actor?.gameProfile?.name ?: "SERVER", target, details)
    }

    /** Records an action performed from a command source (player or console). */
    fun record(action: String, source: CommandSourceStack, target: UUID?, details: String) {
        val player = source.player
        record(action, player?.uuid, player?.gameProfile?.name ?: "CONSOLE", target, details)
    }

    fun record(action: String, actor: UUID?, actorName: String, target: UUID?, details: String) {
        val event = AuditEvent(action, actor, actorName, target, details)
        OneBlockCore.eventBus.post(event)

        val line = buildString {
            append('{')
            append("\"time\":\"").append(Instant.ofEpochMilli(event.timestamp())).append("\",")
            append("\"action\":\"").append(escape(action)).append("\",")
            append("\"actor\":\"").append(escape(actorName)).append("\",")
            append("\"actor_uuid\":").append(actor?.let { "\"$it\"" } ?: "null").append(',')
            append("\"target_uuid\":").append(target?.let { "\"$it\"" } ?: "null").append(',')
            append("\"details\":\"").append(escape(details)).append('"')
            append('}')
        }

        val webhookUrl = OneBlockCore.configManager.mainConfig.discordWebhookUrl
        writer.execute {
            appendToFile(line)
            if (webhookUrl.isNotBlank()) {
                postToDiscord(webhookUrl, action, actorName, details)
            }
        }
    }

    private fun appendToFile(line: String) {
        try {
            Files.createDirectories(file.parent)
            Files.writeString(
                file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND,
            )
        } catch (e: IOException) {
            OneBlockCore.LOGGER.error("Could not write audit log: {}", e.message)
        }
    }

    private fun postToDiscord(url: String, action: String, actorName: String, details: String) {
        val content = escape("**$action** by `$actorName` — $details")
        val body = "{\"content\":\"$content\"}"
        try {
            val request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            if (response.statusCode() !in 200..299) {
                OneBlockCore.LOGGER.warn("Discord webhook returned status {}.", response.statusCode())
            }
        } catch (e: Exception) {
            OneBlockCore.LOGGER.warn("Discord webhook failed: {}", e.message)
        }
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")
}
