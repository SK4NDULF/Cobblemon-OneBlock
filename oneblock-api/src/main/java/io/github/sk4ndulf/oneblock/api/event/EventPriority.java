package io.github.sk4ndulf.oneblock.api.event;

/**
 * Listener execution order. {@link #HIGH} runs first, {@link #LOW} runs last —
 * later listeners see (and may override) the decisions of earlier ones.
 */
public enum EventPriority {
    HIGH,
    NORMAL,
    LOW
}
