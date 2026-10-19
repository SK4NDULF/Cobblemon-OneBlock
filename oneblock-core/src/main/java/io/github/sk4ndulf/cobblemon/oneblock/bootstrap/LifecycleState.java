package io.github.sk4ndulf.cobblemon.oneblock.bootstrap;

/** Startup/runtime states from spec 13 (boot/shutdown) and spec 11 §32. */
public enum LifecycleState {
    /** Mod initialised, storage not yet open. Nothing may mutate. */
    STARTING,
    /** Storage and config are healthy but the setup wizard has not been completed. No gameplay. */
    SETUP_PENDING,
    /** Fully operational. */
    READY,
    /** Storage is degraded: reads may work, all critical writes are refused. */
    READ_ONLY,
    /** Data could not be trusted (binding mismatch, failed migration, unknown open operations, broken DB). No gameplay. */
    RECOVERY_MODE,
    /** Server is shutting down. Nothing may start. */
    STOPPING;

    /** Whether new player-visible mutations (island operations, rewards, ...) may start. */
    public boolean allowsMutations() {
        return this == READY;
    }

    public boolean isTerminal() {
        return this == STOPPING;
    }
}
