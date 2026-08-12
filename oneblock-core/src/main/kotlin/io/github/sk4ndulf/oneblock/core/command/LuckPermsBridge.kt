package io.github.sk4ndulf.oneblock.core.command

import io.github.sk4ndulf.oneblock.core.OneBlockCore
import net.minecraft.server.level.ServerPlayer
import java.lang.reflect.Method
import java.util.UUID

/**
 * Optional LuckPerms support without a compile-time dependency.
 *
 * Reflection keeps the mod drag-and-drop: no extra library to install, and on a server
 * without LuckPerms every lookup simply reports "undefined" so the caller falls back to
 * vanilla OP levels. The reflective handles are resolved once and cached.
 */
object LuckPermsBridge {

    /** Result of a node lookup — mirrors LuckPerms' Tristate. */
    enum class Result { ALLOW, DENY, UNDEFINED }

    private var initialized = false
    private var available = false

    private var luckPerms: Any? = null
    private var getUserMethod: Method? = null
    private var getCachedDataMethod: Method? = null
    private var getPermissionDataMethod: Method? = null
    private var checkPermissionMethod: Method? = null

    private fun init() {
        if (initialized) return
        initialized = true
        try {
            val providerClass = Class.forName("net.luckperms.api.LuckPermsProvider")
            val api = providerClass.getMethod("get").invoke(null)
            val userManager = api.javaClass.getMethod("getUserManager").invoke(api)

            getUserMethod = userManager.javaClass.getMethod("getUser", UUID::class.java)
                .also { it.isAccessible = true }
            luckPerms = userManager

            // Resolved lazily on the first successful user lookup — the concrete
            // implementation classes are not part of the public API surface.
            available = true
            OneBlockCore.LOGGER.info("LuckPerms detected — permission nodes are active.")
        } catch (_: ClassNotFoundException) {
            OneBlockCore.LOGGER.info("No LuckPerms found — falling back to vanilla OP levels.")
        } catch (e: Exception) {
            OneBlockCore.LOGGER.warn("LuckPerms present but could not be hooked ({}) — using OP levels.", e.message)
        }
    }

    /** Looks up a node for a player. Returns UNDEFINED when LuckPerms is absent or silent. */
    fun check(player: ServerPlayer, node: String): Result {
        init()
        if (!available) return Result.UNDEFINED
        return try {
            val userManager = luckPerms ?: return Result.UNDEFINED
            val user = getUserMethod?.invoke(userManager, player.uuid) ?: return Result.UNDEFINED

            val cachedData = (getCachedDataMethod ?: user.javaClass.getMethod("getCachedData")
                .also { it.isAccessible = true; getCachedDataMethod = it }).invoke(user)

            val permissionData = (getPermissionDataMethod ?: cachedData.javaClass.getMethod("getPermissionData")
                .also { it.isAccessible = true; getPermissionDataMethod = it }).invoke(cachedData)

            val tristate = (checkPermissionMethod ?: permissionData.javaClass
                .getMethod("checkPermission", String::class.java)
                .also { it.isAccessible = true; checkPermissionMethod = it })
                .invoke(permissionData, node)

            when ((tristate as? Enum<*>)?.name) {
                "TRUE" -> Result.ALLOW
                "FALSE" -> Result.DENY
                else -> Result.UNDEFINED
            }
        } catch (e: Exception) {
            OneBlockCore.LOGGER.warn("LuckPerms lookup for '{}' failed ({}) — using OP level.", node, e.message)
            Result.UNDEFINED
        }
    }
}
