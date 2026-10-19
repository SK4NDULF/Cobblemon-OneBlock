package io.github.sk4ndulf.cobblemon.oneblock.spike;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Comparator;
import java.util.Optional;

import static io.github.sk4ndulf.cobblemon.oneblock.spike.SpikeMod.LOG;

/** Phase 0B spike: server-side battle start (wild + NPC) and battle/capture event visibility. */
final class BattleSpike {
    private BattleSpike() {
    }

    static void register() {
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(e ->
                LOG.info("SPIKE-BATTLE started_pre battle={} actors={}", e.getBattle().getBattleId(), e.getBattle().getActors()));
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(e ->
                LOG.info("SPIKE-BATTLE started_post battle={} format={}", e.getBattle().getBattleId(), e.getBattle().getFormat()));
        CobblemonEvents.BATTLE_FLED.subscribe(e ->
                LOG.info("SPIKE-BATTLE fled battle={}", e.getBattle().getBattleId()));
        CobblemonEvents.BATTLE_VICTORY.subscribe(e ->
                LOG.info("SPIKE-BATTLE victory battle={} winners={}", e.getBattle().getBattleId(), e.getWinners()));
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(e ->
                LOG.info("SPIKE-CAPTURE ball_hit target={} wild={}", e.getPokemon().getPokemon().getSpecies().getName(),
                        e.getPokemon().getPokemon().isWild()));
        CobblemonEvents.POKEMON_CAPTURED.subscribe(e ->
                LOG.info("SPIKE-CAPTURE captured species={} player={} playerOwnedNow={}", e.getPokemon().getSpecies().getName(),
                        e.getPlayer().getGameProfile().getName(), e.getPokemon().isPlayerOwned()));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
    }

    private static <T extends Entity> Optional<T> nearest(ServerPlayer player, Class<T> type, java.util.function.Predicate<T> filter) {
        return player.serverLevel().getEntitiesOfClass(type, player.getBoundingBox().inflate(64), filter::test).stream()
                .min(Comparator.comparingDouble(player::distanceToSqr));
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spikebattle")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("wild").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                    var target = nearest(player, PokemonEntity.class, p -> p.getPokemon().isWild() && !p.isBattling());
                    if (target.isEmpty()) {
                        LOG.info("SPIKE-BATTLE wild: no wild pokemon near {}", player.getGameProfile().getName());
                        return 0;
                    }
                    var result = BattleBuilder.INSTANCE.pve(player, target.get());
                    LOG.info("SPIKE-BATTLE wild start result={} target={}", result.getClass().getSimpleName(),
                            target.get().getPokemon().getSpecies().getName());
                    result.ifErrored(err -> {
                        LOG.info("SPIKE-BATTLE wild errors={}", err.getParticipantErrors());
                        return kotlin.Unit.INSTANCE;
                    });
                    return 1;
                })))
                .then(Commands.literal("npc").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                    var target = nearest(player, NPCEntity.class, n -> true);
                    if (target.isEmpty()) {
                        LOG.info("SPIKE-BATTLE npc: no npc near {}", player.getGameProfile().getName());
                        return 0;
                    }
                    var result = BattleBuilder.INSTANCE.pvn(player, target.get());
                    LOG.info("SPIKE-BATTLE npc start result={} npcClass={}", result.getClass().getSimpleName(),
                            target.get().getNpc().getId());
                    result.ifErrored(err -> {
                        LOG.info("SPIKE-BATTLE npc errors={}", err.getParticipantErrors());
                        return kotlin.Unit.INSTANCE;
                    });
                    return 1;
                })))
                .then(Commands.literal("aim").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                    var target = nearest(player, PokemonEntity.class, p -> p.getPokemon().isWild() && !p.isBattling());
                    if (target.isEmpty()) {
                        LOG.info("SPIKE-CAPTURE aim: no wild pokemon near {}", player.getGameProfile().getName());
                        return 0;
                    }
                    var t = target.get();
                    player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                            t.position().add(0, t.getBbHeight() / 2, 0));
                    LOG.info("SPIKE-CAPTURE aim at species={} uuid={} pos={} distance={}", t.getPokemon().getSpecies().getName(),
                            t.getUUID(), t.blockPosition().toShortString(), Math.sqrt(player.distanceToSqr(t)));
                    return 1;
                })))
                .then(Commands.literal("end").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                    var battle = BattleRegistry.INSTANCE.getBattleByParticipatingPlayer(player);
                    if (battle == null) {
                        ctx.getSource().sendSuccess(() -> Component.literal("no battle"), false);
                        return 0;
                    }
                    LOG.info("SPIKE-BATTLE stopping battle={} ended={}", battle.getBattleId(), battle.getEnded());
                    battle.stop();
                    return 1;
                }))));
    }
}
