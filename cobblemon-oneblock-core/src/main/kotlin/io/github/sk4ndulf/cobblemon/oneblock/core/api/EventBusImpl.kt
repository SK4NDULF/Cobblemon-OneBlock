package io.github.sk4ndulf.cobblemon.oneblock.core.api

import io.github.sk4ndulf.cobblemon.oneblock.api.event.EventPriority
import io.github.sk4ndulf.cobblemon.oneblock.api.event.EventSubscription
import io.github.sk4ndulf.cobblemon.oneblock.api.event.OneBlockEvent
import io.github.sk4ndulf.cobblemon.oneblock.api.event.OneBlockEventBus
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

/**
 * Event bus implementation.
 *
 * Listeners are stored per subscribed class. When an event is posted, the class hierarchy
 * of the event (up to and including [OneBlockEvent]) is walked, so a listener subscribed
 * to a superclass also receives subclass events. Listener exceptions are caught and
 * logged — a broken addon listener never takes down the core or other listeners.
 */
class EventBusImpl(private val logger: Logger) : OneBlockEventBus {

    private val listeners = ConcurrentHashMap<Class<*>, CopyOnWriteArrayList<Registration<*>>>()

    private inner class Registration<T : OneBlockEvent>(
        val eventType: Class<T>,
        val priority: EventPriority,
        val listener: Consumer<T>,
    ) : EventSubscription {

        @Volatile
        private var active = true

        override fun unsubscribe() {
            if (!active) return
            active = false
            listeners[eventType]?.remove(this)
        }

        override fun isActive(): Boolean = active

        @Suppress("UNCHECKED_CAST")
        fun invoke(event: OneBlockEvent) {
            listener.accept(event as T)
        }
    }

    override fun <T : OneBlockEvent> subscribe(
        eventType: Class<T>,
        priority: EventPriority,
        listener: Consumer<T>,
    ): EventSubscription {
        val registration = Registration(eventType, priority, listener)
        listeners.computeIfAbsent(eventType) { CopyOnWriteArrayList() }.add(registration)
        return registration
    }

    override fun <T : OneBlockEvent> post(event: T): T {
        val matching = ArrayList<Registration<*>>()
        var type: Class<*>? = event.javaClass
        while (type != null && OneBlockEvent::class.java.isAssignableFrom(type)) {
            listeners[type]?.let { matching.addAll(it) }
            type = type.superclass
        }
        if (matching.isEmpty()) return event

        matching.sortBy { it.priority.ordinal }
        for (registration in matching) {
            try {
                registration.invoke(event)
            } catch (e: Exception) {
                logger.error(
                    "Event listener for {} threw an exception (event: {})",
                    registration.eventType.simpleName, event.javaClass.simpleName, e,
                )
            }
        }
        return event
    }
}
