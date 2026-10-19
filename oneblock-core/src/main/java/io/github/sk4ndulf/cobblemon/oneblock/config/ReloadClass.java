package io.github.sk4ndulf.cobblemon.oneblock.config;

/** When a changed config value takes effect (spec 12 §16). */
public enum ReloadClass {
    /** Applied by {@code /ob reload}. */
    LIVE,
    /** Validated by {@code /ob reload} but only applied after a server restart. */
    RESTART,
    /** May not change once the first island exists; a reload with a changed value is rejected. */
    WORLD_LOCKED
}
