package io.github.sk4ndulf.oneblock.api.trigger;

/** Result of one tick of an active trigger event. */
public enum TriggerEventStatus {
    /** The event keeps running. */
    RUNNING,
    /** The event was completed successfully. Rewards are the event's own responsibility. */
    SUCCESS,
    /** The event failed. The core cleans up and applies the cooldown; no rewards. */
    FAILURE
}
