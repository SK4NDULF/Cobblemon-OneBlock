package io.github.sk4ndulf.cobblemon.oneblock.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbExecutorTest {
    @TempDir
    Path dir;

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private DbExecutor openExecutor(int capacity) throws Exception {
        DbExecutor db = new DbExecutor(capacity);
        db.open(() -> SqliteConnections.open(dir.resolve("t.db"), 1000)).get(10, TimeUnit.SECONDS);
        return db;
    }

    @Test
    void workRunsInOrderOnTheDbThread() throws Exception {
        try (DbExecutor db = openExecutor(64)) {
            List<Integer> order = Collections.synchronizedList(new ArrayList<>());
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                int n = i;
                futures.add(db.submit("t" + i, c -> {
                    order.add(n);
                    assertTrue(db.isDbThread());
                    return Thread.currentThread().getName();
                }));
            }
            for (CompletableFuture<String> f : futures) {
                String thread = f.get(10, TimeUnit.SECONDS);
                assertTrue(thread.startsWith("cobblemon-oneblock-db-"), thread);
                assertNotEquals(Thread.currentThread().getName(), thread);
            }
            for (int i = 0; i < 20; i++) {
                assertEquals(i, order.get(i));
            }
        }
    }

    @Test
    void fullQueueFailsVisiblyInsteadOfGrowing() throws Exception {
        try (DbExecutor db = openExecutor(2)) {
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch started = new CountDownLatch(1);
            CompletableFuture<Void> blocker = db.submit("block", c -> {
                started.countDown();
                await(release);
                return null;
            });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            CompletableFuture<Integer> q1 = db.submit("q1", c -> 1);
            CompletableFuture<Integer> q2 = db.submit("q2", c -> 2);
            CompletableFuture<Integer> rejected = db.submit("q3", c -> 3);
            ExecutionException e = assertThrows(ExecutionException.class, () -> rejected.get(1, TimeUnit.SECONDS));
            assertInstanceOf(StorageException.class, e.getCause());
            assertTrue(e.getCause().getMessage().contains("queue is full"), e.getCause().getMessage());
            release.countDown();
            blocker.get(10, TimeUnit.SECONDS);
            assertEquals(1, q1.get(10, TimeUnit.SECONDS));
            assertEquals(2, q2.get(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void shutdownDrainsQueuedWorkThenRefusesNewWork() throws Exception {
        DbExecutor db = openExecutor(64);
        List<CompletableFuture<Integer>> queued = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int n = i;
            queued.add(db.submit("w" + i, c -> {
                pause(5);
                return n;
            }));
        }
        assertTrue(db.shutdown(Duration.ofSeconds(10)));
        for (int i = 0; i < 10; i++) {
            assertTrue(queued.get(i).isDone());
            assertEquals(i, queued.get(i).get());
        }
        CompletableFuture<Integer> late = db.submit("late", c -> 1);
        ExecutionException e = assertThrows(ExecutionException.class, late::get);
        assertTrue(e.getCause().getMessage().contains("shutting down"), e.getCause().getMessage());
    }

    @Test
    void shutdownTimeoutLeavesConnectionToTheRunningTask() throws Exception {
        DbExecutor db = openExecutor(4);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Boolean> stillOpen = db.submit("slow", c -> {
            await(release);
            return !c.isClosed();
        });
        assertFalse(db.shutdown(Duration.ofMillis(200)));
        release.countDown();
        assertTrue(stillOpen.get(10, TimeUnit.SECONDS), "connection must not be closed under a running task");
        assertTrue(db.shutdown(Duration.ofSeconds(10)));
    }

    @Test
    void submitBeforeOpenFails() {
        DbExecutor db = new DbExecutor(4);
        ExecutionException e = assertThrows(ExecutionException.class, () -> db.submit("x", c -> 1).get(5, TimeUnit.SECONDS));
        assertInstanceOf(StorageException.class, e.getCause());
        db.shutdown(Duration.ofSeconds(5));
    }
}
