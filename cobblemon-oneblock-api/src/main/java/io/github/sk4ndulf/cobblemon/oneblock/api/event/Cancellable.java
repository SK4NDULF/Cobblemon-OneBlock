package io.github.sk4ndulf.cobblemon.oneblock.api.event;

/**
 * Implemented by events whose default behaviour can be prevented by listeners.
 *
 * <p>A cancelled event is still delivered to the remaining listeners (so they can observe
 * the cancellation), but the core mod will not perform the default action.</p>
 */
public interface Cancellable {

    boolean isCancelled();

    void setCancelled(boolean cancelled);
}
