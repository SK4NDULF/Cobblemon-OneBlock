package io.github.sk4ndulf.cobblemon.oneblock.storage;

import io.github.sk4ndulf.cobblemon.oneblock.util.AtomicFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Properties;
import java.util.UUID;

/**
 * Proves that the database belongs to this world (spec 11 §5): an installation id stored both in the database and in
 * a marker file in the world folder, plus the world seed. Runs on the DB thread; it only reads and writes its own
 * marker file and never touches Minecraft data.
 *
 * <p>Decision table (db = database file existed before this start, marker = marker file exists):
 * <ul>
 *   <li>no db, no marker: fresh install, create both.</li>
 *   <li>no db, marker: the world was initialised but its database is gone. Refuse, never start empty.</li>
 *   <li>db, no marker: allowed only if the seed matches (crash between DB commit and marker write); marker is rewritten.</li>
 *   <li>db, marker: installation id and seed must match.</li>
 * </ul>
 */
public final class WorldBinding {
    public static final String MARKER_FILE = "cobblemon_oneblock.world-id";
    static final String KEY_INSTALLATION_ID = "installation_id";
    static final String KEY_WORLD_SEED = "world_seed";
    static final String KEY_CREATED_AT = "created_at";
    static final String KEY_CREATED_BY = "created_by_version";
    public static final String KEY_SETUP_COMPLETED = "setup_completed";

    private static final Logger LOG = LoggerFactory.getLogger("cobblemon_oneblock");

    public record Result(UUID installationId, boolean freshlyCreated, boolean markerRewritten, boolean setupCompleted) {
    }

    private final Clock clock;
    private final String modVersion;

    public WorldBinding(Clock clock, String modVersion) {
        this.clock = clock;
        this.modVersion = modVersion;
    }

    /**
     * @param dbExistedBefore whether the database file existed before it was opened for this start
     */
    public Result bind(Connection c, Path worldRoot, long worldSeed, boolean dbExistedBefore) throws SQLException {
        Path marker = worldRoot.resolve(MARKER_FILE);
        Properties markerProps = readMarker(marker);

        if (!dbExistedBefore && markerProps != null) {
            throw new StorageException("this world was set up for Cobblemon OneBlock (marker " + MARKER_FILE
                    + ") but its database is missing. Restore the database from a backup. A new empty database is "
                    + "not created automatically, because that would silently forget all islands.", true);
        }

        String dbId = meta(c, KEY_INSTALLATION_ID);
        String dbSeed = meta(c, KEY_WORLD_SEED);

        if (dbId == null) {
            // Only a database without any data may be initialised (fresh file, or crash right after the migration).
            if (Sql.count(c, "operations") > 0 || Sql.count(c, "audit_log") > 0) {
                throw new StorageException("database contains data but no installation id", true);
            }
            if (markerProps != null) {
                throw new StorageException("marker file exists but the database has no installation id", true);
            }
            UUID id = UUID.randomUUID();
            Sql.inTransaction(c, tx -> {
                putMeta(tx, KEY_INSTALLATION_ID, id.toString());
                putMeta(tx, KEY_WORLD_SEED, Long.toString(worldSeed));
                putMeta(tx, KEY_CREATED_AT, Long.toString(clock.millis()));
                putMeta(tx, KEY_CREATED_BY, modVersion);
                return null;
            });
            writeMarker(marker, id, worldSeed);
            LOG.info("Bound new Cobblemon OneBlock database to this world (installation {})", id);
            return new Result(id, true, false, false);
        }

        if (dbSeed == null || !dbSeed.equals(Long.toString(worldSeed))) {
            throw new StorageException("database belongs to a world with seed " + dbSeed + ", but this world has seed "
                    + worldSeed + ". Was the database copied from another world?", true);
        }
        UUID id = UUID.fromString(dbId);
        boolean rewritten = false;
        if (markerProps == null) {
            LOG.warn("Marker file {} was missing; seed matches, recreating it", MARKER_FILE);
            writeMarker(marker, id, worldSeed);
            rewritten = true;
        } else if (!dbId.equals(markerProps.getProperty(KEY_INSTALLATION_ID))) {
            throw new StorageException("installation id in the database (" + dbId + ") does not match the marker file ("
                    + markerProps.getProperty(KEY_INSTALLATION_ID) + ")", true);
        }
        boolean setupCompleted = "true".equals(meta(c, KEY_SETUP_COMPLETED));
        return new Result(id, false, rewritten, setupCompleted);
    }

    static String meta(Connection c, String key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT value FROM world_meta WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    static void putMeta(Connection c, String key, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO world_meta (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    private static Properties readMarker(Path marker) {
        if (!Files.exists(marker)) {
            return null;
        }
        try {
            Properties p = new Properties();
            p.load(new StringReader(Files.readString(marker, StandardCharsets.UTF_8)));
            if (p.getProperty(KEY_INSTALLATION_ID) == null) {
                throw new StorageException("marker file " + marker + " has no " + KEY_INSTALLATION_ID, true);
            }
            return p;
        } catch (IOException e) {
            throw new StorageException("cannot read marker file " + marker, e, true);
        }
    }

    private void writeMarker(Path marker, UUID id, long seed) {
        String content = "# Cobblemon OneBlock world marker. Do not edit or copy to another world.\n"
                + KEY_INSTALLATION_ID + "=" + id + "\n"
                + KEY_WORLD_SEED + "=" + seed + "\n"
                + KEY_CREATED_BY + "=" + modVersion + "\n";
        try {
            AtomicFiles.writeString(marker, content);
        } catch (IOException e) {
            throw new StorageException("cannot write marker file " + marker, e, false);
        }
    }
}
