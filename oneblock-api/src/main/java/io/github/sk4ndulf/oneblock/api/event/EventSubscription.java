package io.github.sk4ndulf.oneblock.api.event;

/**
 * Handle returned by {@link OneBlockEventBus#subscribe}. Keep it if the listener
 * should be removable later (e.g. when an addon feature is disabled at runtime).
 */
public interface EventSubscription {

    /**
     * Removes the listener from the bus. Calling this more than once is a no-op.
     */
    void unsubscribe();

    boolean isActive();
}
