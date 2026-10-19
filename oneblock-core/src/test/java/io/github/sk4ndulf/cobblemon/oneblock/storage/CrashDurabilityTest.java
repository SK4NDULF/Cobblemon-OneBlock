package io.github.sk4ndulf.cobblemon.oneblock.storage;

import io.github.sk4ndulf.cobblemon.oneblock.operation.OperationJournal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hard-kill test (spec 14 §21, failure path): a child JVM commits journal rows in a loop and is killed with SIGKILL
 * ({@link Process#destroyForcibly()}) at a random moment, three times in a row on the same database.
 * After every kill: integrity check ok, and every row the child acknowledged after its commit is present.
 *
 * <p>Limit: this simulates a crashed process, not a power loss or OS crash (the OS page cache survives).
 */
class CrashDurabilityTest {
    @TempDir
    Path dir;

    @Test
    void acknowledgedCommitsSurviveSigkill() throws Exception {
        String classpath = System.getProperty("oneblock.test.classpath");
        assertNotNull(classpath, "run through Gradle (oneblock.test.classpath)");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path db = dir.resolve("oneblock.db");
        Path ack = dir.resolve("ack.txt");
        Files.createFile(ack);
        int next = 0;
        for (int round = 0; round < 3; round++) {
            Process child = new ProcessBuilder(java, "-cp", classpath, CrashWriterMain.class.getName(),
                    db.toString(), ack.toString(), Integer.toString(next))
                    .redirectErrorStream(true)
                    .redirectOutput(dir.resolve("child-" + round + ".log").toFile())
                    .start();
            int target = next + 50 + ThreadLocalRandom.current().nextInt(200);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (acked(ack).size() < target && System.nanoTime() < deadline && child.isAlive()) {
                Thread.sleep(5);
            }
            int finishedRound = round;
            assertTrue(child.isAlive(), () -> "child died on its own: " + readLog(finishedRound));
            child.destroyForcibly();
            assertTrue(child.waitFor(30, TimeUnit.SECONDS));

            List<Integer> acked = acked(ack);
            assertTrue(acked.size() >= target - next, "child did not write enough rows: " + acked.size());
            try (Connection c = SqliteConnections.open(db, 5000)) {
                assertEquals("ok", Sql.pragma(c, "integrity_check"));
                OperationJournal journal = new OperationJournal(Clock.systemUTC());
                for (int i : acked) {
                    assertTrue(journal.findByRequestId(c, "crash-" + i).isPresent(), "acknowledged row " + i + " lost");
                }
                // Each kill may hit between a commit and its acknowledgement: at most one unacknowledged row per round.
                long rows = Sql.count(c, "operations");
                long unacknowledged = rows - acked.size();
                assertTrue(unacknowledged >= 0 && unacknowledged <= round + 1,
                        "rows " + rows + " vs acked " + acked.size() + " after round " + round);
                next = (int) rows;
            }
        }
    }

    private static List<Integer> acked(Path ack) throws Exception {
        // A torn last line (killed mid-write) is ignored: it was never fully acknowledged.
        String text = Files.readString(ack);
        int end = text.lastIndexOf('\n');
        if (end < 0) {
            return List.of();
        }
        return text.substring(0, end).lines().filter(l -> !l.isBlank()).map(Integer::parseInt).toList();
    }

    private String readLog(int round) {
        try {
            return Files.readString(dir.resolve("child-" + round + ".log"));
        } catch (Exception e) {
            return "<no log>";
        }
    }
}
