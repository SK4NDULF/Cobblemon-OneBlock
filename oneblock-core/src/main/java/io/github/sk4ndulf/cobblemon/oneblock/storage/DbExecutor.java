package io.github.sk4ndulf.cobblemon.oneblock.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The only thread that touches SQLite (ADR 004). One daemon thread, one connection, a bounded queue.
 *
 * <p>Contract: work runs strictly in submission order; results are completed on the DB thread, so callers hop back
 * to the server thread themselves (e.g. {@code thenAcceptAsync(fn, server)}). Work must never touch Minecraft or
 * Cobblemon objects and must return immutable values. A full queue fails the returned future immediately with
 * {@link StorageException}; nothing is dropped silently.
 */
public final class DbExecutor implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("cobblemon_oneblock");
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private final ThreadPoolExecutor executor;
    private final int capacity;
    private volatile Connection connection;
    private volatile Thread dbThread;

    public DbExecutor(int queueCapacity) {
        this.capacity = queueCapacity;
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                r -> {
                    Thread t = new Thread(r, "cobblemon-oneblock-db-" + THREAD_COUNTER.incrementAndGet());
                    t.setDaemon(true);
                    dbThread = t;
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Opens the connection on the DB thread. Must be the first submitted task. */
    public CompletableFuture<Void> open(ConnectionFactory factory) {
        return submit("open", ignored -> {
            if (connection != null) {
                throw new IllegalStateException("already open");
            }
            connection = factory.open();
            return null;
        }, true);
    }

    @FunctionalInterface
    public interface ConnectionFactory {
        Connection open() throws SQLException;
    }

    public <T> CompletableFuture<T> submit(String name, Work<T> work) {
        return submit(name, work, false);
    }

    private <T> CompletableFuture<T> submit(String name, Work<T> work, boolean opening) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    Connection c = connection;
                    if (c == null && !opening) {
                        throw new StorageException("database is not open (task '" + name + "')", false);
                    }
                    future.complete(work.run(c));
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (RejectedExecutionException e) {
            String reason = executor.isShutdown() ? "database is shutting down" : "database queue is full (" + capacity + ")";
            future.completeExceptionally(new StorageException(reason + ", task '" + name + "' refused", e, false));
        }
        return future;
    }

    public boolean isDbThread() {
        return Thread.currentThread() == dbThread;
    }

    public int queuedTasks() {
        return executor.getQueue().size();
    }

    public int capacity() {
        return capacity;
    }

    /**
     * Stops accepting work, runs everything already queued, then closes the connection. Blocks the calling thread up
     * to {@code timeout}. This is the one intended blocking wait (server shutdown); the DB thread never waits for the
     * server thread, so it cannot deadlock.
     *
     * @return true if all queued work finished and the connection was closed in time
     */
    public boolean shutdown(Duration timeout) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                LOG.error("Database did not finish within {} s; {} tasks still queued, connection left open",
                        timeout.toSeconds(), queuedTasks());
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        // The DB thread has terminated (awaitTermination gives happens-before), so closing here is safe.
        closeConnectionQuietly();
        return true;
    }

    @Override
    public void close() {
        shutdown(Duration.ofSeconds(30));
    }

    private void closeConnectionQuietly() {
        Connection c = connection;
        connection = null;
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (SQLException e) {
            LOG.error("Closing the database failed", e);
        }
    }
}
