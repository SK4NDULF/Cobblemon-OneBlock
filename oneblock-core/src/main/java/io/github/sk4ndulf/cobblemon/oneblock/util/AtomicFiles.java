package io.github.sk4ndulf.cobblemon.oneblock.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Crash-safe file replacement: write a temp file in the same directory, fsync it, then atomically rename it. */
public final class AtomicFiles {
    private AtomicFiles() {
    }

    public static void writeString(Path target, String content) throws IOException {
        write(target, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void write(Path target, byte[] content) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, "." + target.getFileName() + ".", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("Filesystem does not support atomic rename for " + target, e);
            }
            fsyncDirectory(dir);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Persists the directory entry of a rename. Not possible on every OS (e.g. Windows); then the rename is still atomic. */
    private static void fsyncDirectory(Path dir) {
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Directory channels are not supported everywhere; the atomic rename above already happened.
        }
    }
}
