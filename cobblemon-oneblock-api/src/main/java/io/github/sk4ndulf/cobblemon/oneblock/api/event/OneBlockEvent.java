package io.github.sk4ndulf.cobblemon.oneblock.api.event;

/**
 * Base class for all events fired on the {@link OneBlockEventBus}.
 *
 * <p>Concrete events (e.g. {@code IslandCreatedEvent}, {@code OneBlockBreakEvent}) are added
 * per feature module and live in this package. Events are always dispatched on the server
 * thread unless their documentation explicitly states otherwise.</p>
 */
public abstract class OneBlockEvent {
}
