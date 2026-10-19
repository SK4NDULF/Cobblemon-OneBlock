package io.github.sk4ndulf.cobblemon.oneblock.spike;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Phase 0B feasibility spike. Not production code. */
public final class SpikeMod implements ModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("oneblock-spike");
    static final ResourceKey<Level> VOID = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("cobblemon_oneblock", "void"));

    static final AtomicInteger SPAWNS_VOID = new AtomicInteger();
    static final AtomicInteger SPAWNS_OTHER = new AtomicInteger();
    static final AtomicInteger SPAWNS_IN_HUB = new AtomicInteger();
    static final AtomicInteger HUB_POSITIONS_BLOCKED = new AtomicInteger();
    static final Map<String, Integer> BY_BIOME_SPECIES = new ConcurrentHashMap<>();

    private static final ExecutorService DB = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "oneblock-spike-db");
        t.setDaemon(true);
        return t;
    });

    @Override
    public void onInitialize() {
        CobblemonEvents.ENTITY_SPAWN.subscribe(event -> {
            if (!(event.getEntity() instanceof PokemonEntity pokemon)) {
                return;
            }
            var position = event.getSpawnablePosition();
            ServerLevel world = position.getWorld();
            var pos = position.getPosition();
            boolean isVoid = world.dimension().equals(VOID);
            (isVoid ? SPAWNS_VOID : SPAWNS_OTHER).incrementAndGet();
            if (isVoid && HubFilterInfluence.isHub(pos)) {
                SPAWNS_IN_HUB.incrementAndGet();
            }
            String biome = position.getBiomeName().toString();
            String species = pokemon.getPokemon().getSpecies().getName();
            BY_BIOME_SPECIES.merge(world.dimension().location() + " | " + biome + " | " + species, 1, Integer::sum);
            LOG.info("SPIKE-SPAWN dim={} pos={} biome={} species={} level={} type={} cause={}",
                    world.dimension().location(), pos.toShortString(), biome, species,
                    pokemon.getPokemon().getLevel(), position.getClass().getSimpleName(),
                    position.getCause().getClass().getSimpleName());
        });

        PlayerSpawnerFactory.INSTANCE.getInfluenceBuilders().add(player -> new HubFilterInfluence());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(SpikeMod::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOG.info("SPIKE-STATS {}", stats());
            DB.shutdown();
        });
        BattleSpike.register();
        HologramSpike.register();
        ShutdownProbe.register();
    }

    private static void onServerStarted(MinecraftServer server) {
        ServerLevel voidLevel = server.getLevel(VOID);
        if (voidLevel == null) {
            LOG.error("SPIKE-DIM void level missing");
        } else {
            var type = voidLevel.dimensionType();
            LOG.info("SPIKE-DIM void level present: minY={} height={} skylight={} ceiling={} natural={} bedWorks={} anchorWorks={} fixedTime={} effects={} generator={} biomeSource={}",
                    type.minY(), type.height(), type.hasSkyLight(), type.hasCeiling(), type.natural(),
                    type.bedWorks(), type.respawnAnchorWorks(), type.hasFixedTime(), type.effectsLocation(),
                    voidLevel.getChunkSource().getGenerator().getClass().getSimpleName(),
                    voidLevel.getChunkSource().getGenerator().getBiomeSource().getClass().getSimpleName());
        }
        for (ServerLevel level : server.getAllLevels()) {
            LOG.info("SPIKE-DIM loaded level {}", level.dimension().location());
        }

        Path dbFile = server.getWorldPath(LevelResource.ROOT).resolve("data/oneblock_spike/spike.db").toAbsolutePath().normalize();
        DB.execute(() -> sqliteSmokeTest(server, dbFile));
    }

    private static void sqliteSmokeTest(MinecraftServer server, Path dbFile) {
        boolean onServerThread = server.isSameThread();
        try {
            Files.createDirectories(dbFile.getParent());
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
                 Statement s = c.createStatement()) {
                String journal;
                try (ResultSet rs = s.executeQuery("PRAGMA journal_mode=WAL")) {
                    journal = rs.next() ? rs.getString(1) : "?";
                }
                s.execute("PRAGMA synchronous=FULL");
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("CREATE TABLE IF NOT EXISTS boot_log (id INTEGER PRIMARY KEY, at INTEGER NOT NULL)");
                s.executeUpdate("INSERT INTO boot_log(at) VALUES (" + System.currentTimeMillis() + ")");
                int rows;
                try (ResultSet rs = s.executeQuery("SELECT count(*) FROM boot_log")) {
                    rows = rs.next() ? rs.getInt(1) : -1;
                }
                String version;
                try (ResultSet rs = s.executeQuery("SELECT sqlite_version()")) {
                    version = rs.next() ? rs.getString(1) : "?";
                }
                LOG.info("SPIKE-SQLITE ok file={} journal={} sqlite={} bootRows={} driver={} nativeMode={} onServerThread={} thread={}",
                        dbFile, journal, version, rows, c.getMetaData().getDriverVersion(),
                        org.sqlite.SQLiteJDBCLoader.isNativeMode(), onServerThread, Thread.currentThread().getName());
                int finalRows = rows;
                server.execute(() -> LOG.info("SPIKE-SQLITE callback rows={} onServerThread={}", finalRows, server.isSameThread()));
            }
        } catch (Exception e) {
            LOG.error("SPIKE-SQLITE failed", e);
        }
    }

    static String stats() {
        return "void=" + SPAWNS_VOID.get() + " other=" + SPAWNS_OTHER.get() + " inHub=" + SPAWNS_IN_HUB.get()
                + " hubPositionsBlocked=" + HUB_POSITIONS_BLOCKED.get() + " detail=" + new TreeMap<>(BY_BIOME_SPECIES);
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spike")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("stats").executes(ctx -> {
                    String s = stats();
                    LOG.info("SPIKE-STATS {}", s);
                    ctx.getSource().sendSuccess(() -> Component.literal(s), false);
                    return 1;
                }))
                .then(Commands.literal("reset").executes(ctx -> {
                    SPAWNS_VOID.set(0);
                    SPAWNS_OTHER.set(0);
                    SPAWNS_IN_HUB.set(0);
                    HUB_POSITIONS_BLOCKED.set(0);
                    BY_BIOME_SPECIES.clear();
                    LOG.info("SPIKE-STATS reset");
                    return 1;
                })));
    }
}
