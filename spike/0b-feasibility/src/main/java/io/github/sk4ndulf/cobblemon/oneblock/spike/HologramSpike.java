package io.github.sk4ndulf.cobblemon.oneblock.spike;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.github.sk4ndulf.cobblemon.oneblock.spike.SpikeMod.LOG;

/**
 * Phase 0B spike: packet-only text display. The TextDisplay object is never added to the level, so nothing is saved;
 * its entity data is configured through vanilla NBT loading (setText is private) and sent as vanilla packets.
 * Rendering has to be confirmed by the owner with a real Cobblemon client.
 */
final class HologramSpike {
    private static final List<Integer> SENT_IDS = new ArrayList<>();

    private HologramSpike() {
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spikeholo")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("show").then(Commands.argument("pos", Vec3Argument.vec3())
                        .then(Commands.argument("text", StringArgumentType.greedyString()).executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
                            String text = StringArgumentType.getString(ctx, "text");
                            int sent = show(level, pos, text);
                            ctx.getSource().sendSuccess(() -> Component.literal("holo sent to " + sent + " players"), false);
                            return sent;
                        }))))
                .then(Commands.literal("clear").executes(ctx -> {
                    var packet = new ClientboundRemoveEntitiesPacket(SENT_IDS.stream().mapToInt(Integer::intValue).toArray());
                    ctx.getSource().getServer().getPlayerList().getPlayers().forEach(p -> p.connection.send(packet));
                    LOG.info("SPIKE-HOLO cleared ids={}", SENT_IDS);
                    SENT_IDS.clear();
                    return 1;
                })));
    }

    private static int show(ServerLevel level, Vec3 pos, String text) {
        Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        CompoundTag tag = new CompoundTag();
        tag.putString("text", Component.Serializer.toJson(Component.literal(text), level.registryAccess()));
        tag.putString("billboard", "center");
        display.load(tag);
        display.setPos(pos);

        int id = display.getId();
        UUID uuid = UUID.randomUUID();
        var add = new ClientboundAddEntityPacket(id, uuid, pos.x, pos.y, pos.z, 0f, 0f, EntityType.TEXT_DISPLAY, 0, Vec3.ZERO, 0d);
        var values = display.getEntityData().getNonDefaultValues();
        var data = values == null ? null : new ClientboundSetEntityDataPacket(id, values);
        int sent = 0;
        for (ServerPlayer player : level.players()) {
            player.connection.send(add);
            if (data != null) {
                player.connection.send(data);
            }
            sent++;
        }
        SENT_IDS.add(id);
        LOG.info("SPIKE-HOLO sent id={} dim={} pos={} dataValues={} players={} persistedEntities={}", id,
                level.dimension().location(), pos, values == null ? 0 : values.size(), sent,
                level.getEntity(id) == null ? "none" : "PRESENT");
        return sent;
    }
}
