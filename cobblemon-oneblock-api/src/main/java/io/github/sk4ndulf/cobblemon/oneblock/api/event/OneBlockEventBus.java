package io.github.sk4ndulf.cobblemon.oneblock.api.event;

import java.util.function.Consumer;

/**
 * The central event bus of Cobblemon OneBlock.
 *
 * <p>Listeners are matched by the exact event class <em>or any of its superclasses</em>:
 * subscribing to {@link OneBlockEvent} receives every event on the bus.</p>
 *
 * <p>Listener exceptions are caught and logged by the bus; a failing addon listener never
 * breaks the core mod or other listeners.</p>
 */
public interface OneBlockEventBus {

    /**
     * Subscribes with {@link EventPriority#NORMAL} priority.
     */
    default <T extends OneBlockEvent> EventSubscription subscribe(Class<T> eventType, Consumer<T> listener) {
        return subscribe(eventType, EventPriority.NORMAL, listener);
    }

    /**
     * Subscribes a listener for the given event type.
     *
     * @param eventType the event class to listen for (superclasses match subclass events)
     * @param priority  execution order relative to other listeners of the same event
     * @param listener  the callback; runs on the server thread
     * @return a handle to remove the listener again
     */
    <T extends OneBlockEvent> EventSubscription subscribe(Class<T> eventType, EventPriority priority, Consumer<T> listener);

    /**
     * Fires an event to all matching listeners and returns it (for reading listener
     * mutations such as {@link Cancellable#isCancelled()}).
     *
     * <p>Addons normally only <em>listen</em>; posting is reserved for the core mod and for
     * addons firing their own custom event types.</p>
     */
    <T extends OneBlockEvent> T post(T event);
}
