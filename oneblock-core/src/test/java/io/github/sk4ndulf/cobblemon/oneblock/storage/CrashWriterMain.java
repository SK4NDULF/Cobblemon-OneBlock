package io.github.sk4ndulf.cobblemon.oneblock.storage;

import io.github.sk4ndulf.cobblemon.oneblock.operation.OperationJournal;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;

/**
 * Child process for {@link CrashDurabilityTest}: writes journal operations forever and appends the number of every
 * committed operation to an ack file. The parent kills this process with SIGKILL at a random point.
 *
 * <p>Args: {@code <db file> <ack file> <first index>}
 */
public final class CrashWriterMain {
    private CrashWriterMain() {
    }

    public static void main(String[] args) throws Exception {
        Path db = Path.of(args[0]);
        Path ack = Path.of(args[1]);
        int first = Integer.parseInt(args[2]);
        Clock clock = Clock.systemUTC();
        try (Connection c = SqliteConnections.open(db, 5000);
             OutputStream out = new FileOutputStream(ack.toFile(), true)) {
            new MigrationRunner(clock).migrate(c, db, db.getParent().resolve("backups"));
            OperationJournal journal = new OperationJournal(clock);
            for (int i = first; ; i++) {
                journal.begin(c, new OperationJournal.Request("crash-" + i, "CRASH_TEST", null, "test", "WRITTEN", "{\"i\":" + i + "}"));
                // Only acknowledged after the commit returned.
                out.write((i + "\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
            }
        }
    }
}
