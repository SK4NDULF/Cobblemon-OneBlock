package io.github.sk4ndulf.cobblemon.oneblock.api.trigger;

/**
 * A running instance of a trigger event on one island. Created by
 * {@link TriggerEventType#start}; the core calls {@link #tick} every server tick.
 */
public interface ActiveTriggerEvent {

    /**
     * Advances the event by one tick. Runs on the server thread.
     *
     * <p>Return {@link TriggerEventStatus#SUCCESS} after granting rewards (rewards are the
     * event's responsibility and must NOT scale with difficulty — project rule), or
     * {@link TriggerEventStatus#FAILURE} to abort. Exceptions are caught by the core and
     * treated as failure.</p>
     */
    TriggerEventStatus tick(TriggerEventContext context);

    /**
     * Releases everything the event created (despawn mobs, remove markers). Called exactly
     * once when the event ends — on success, failure, timeout, island archive and server stop.
     */
    void cleanup(TriggerEventContext context);
}
