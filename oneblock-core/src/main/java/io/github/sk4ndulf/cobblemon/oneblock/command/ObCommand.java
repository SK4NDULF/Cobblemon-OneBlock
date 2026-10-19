package io.github.sk4ndulf.cobblemon.oneblock.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.github.sk4ndulf.cobblemon.oneblock.bootstrap.Lifecycle;
import io.github.sk4ndulf.cobblemon.oneblock.bootstrap.ModRuntime;
import io.github.sk4ndulf.cobblemon.oneblock.config.ConfigIssue;
import io.github.sk4ndulf.cobblemon.oneblock.config.ConfigManager;
import io.github.sk4ndulf.cobblemon.oneblock.diagnostics.Diagnostic;
import io.github.sk4ndulf.cobblemon.oneblock.diagnostics.ErrorClass;
import io.github.sk4ndulf.cobblemon.oneblock.localization.Messages;
import io.github.sk4ndulf.cobblemon.oneblock.storage.DbExecutor;
import io.github.sk4ndulf.cobblemon.oneblock.storage.StorageBootstrap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.Supplier;

/**
 * {@code /ob status} (everyone) and {@code /ob reload} (permission level 2). Permissions are checked in the command
 * body as well, not only in {@code requires}, so a future menu or API path cannot skip them (CLAUDE.md rule 5).
 */
public final class ObCommand {
    public static final int ADMIN_LEVEL = 2;
    private static final int MAX_RECENT_SHOWN = 5;

    private ObCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Supplier<ModRuntime> runtime, Messages messages) {
        dispatcher.register(Commands.literal("ob")
                .then(Commands.literal("status").executes(ctx -> status(ctx, runtime.get(), messages)))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(ADMIN_LEVEL))
                        .executes(ctx -> reload(ctx, runtime.get(), messages))));
    }

    static String language(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? Messages.FALLBACK : Messages.resolveLanguage(player.clientInformation().language());
    }

    private static int status(CommandContext<CommandSourceStack> ctx, ModRuntime runtime, Messages messages) {
        CommandSourceStack source = ctx.getSource();
        String lang = language(source);
        if (runtime == null) {
            source.sendFailure(Component.literal(messages.get(lang, "state.desc.STARTING")));
            return 0;
        }
        Lifecycle.Status status = runtime.lifecycle().status();
        String stateName = messages.get(lang, "state." + status.state().name());
        send(source, messages.get(lang, "status.header", runtime.modVersion()));
        send(source, messages.get(lang, "status.state", stateName) + " - " + messages.get(lang, "state.desc." + status.state().name()));
        boolean admin = source.hasPermission(ADMIN_LEVEL);
        for (Diagnostic reason : status.reasons()) {
            // Players only see the diagnostic id; the admin-facing message may contain paths.
            String text = admin ? messages.get(lang, "status.reason_id", reason.message(), reason.id()) : reason.id();
            send(source, messages.get(lang, "status.reason", text));
        }
        if (!admin) {
            return 1;
        }
        StorageBootstrap.Report storage = runtime.storage();
        if (storage == null) {
            send(source, messages.get(lang, "status.admin.db_missing"));
        } else {
            send(source, messages.get(lang, "status.admin.db", storage.dbFile(), storage.migrations().toVersion(),
                    storage.settings().journalMode()));
            send(source, messages.get(lang, "status.admin.open_operations", storage.openOperations().size()));
        }
        DbExecutor db = runtime.db();
        if (db != null) {
            send(source, messages.get(lang, "status.admin.queue", db.queuedTasks(), db.capacity()));
        }
        ConfigManager config = runtime.config();
        send(source, config.valid()
                ? messages.get(lang, "status.admin.config_ok", config.file())
                : messages.get(lang, "status.admin.config_invalid"));
        List<Diagnostic> recent = runtime.diagnostics().recent();
        if (!recent.isEmpty()) {
            send(source, messages.get(lang, "status.admin.recent"));
            recent.stream().limit(MAX_RECENT_SHOWN).forEach(d ->
                    send(source, messages.get(lang, "status.admin.recent_entry", d.id(), d.errorClass(), d.message())));
        }
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx, ModRuntime runtime, Messages messages) {
        CommandSourceStack source = ctx.getSource();
        String lang = language(source);
        if (!source.hasPermission(ADMIN_LEVEL)) {
            source.sendFailure(Component.literal(messages.get(lang, "error.no_permission")));
            return 0;
        }
        if (runtime == null) {
            source.sendFailure(Component.literal(messages.get(lang, "state.desc.STARTING")));
            return 0;
        }
        runtime.reloadConfig().whenComplete((outcome, error) -> {
            // Completed on the server thread (see ModRuntime.reloadConfig), or exceptionally from the I/O thread.
            if (error != null) {
                Diagnostic d = runtime.diagnostics().report(ErrorClass.CONFIG, "reload failed", error);
                source.sendFailure(Component.literal(messages.get(lang, "error.internal", d.id())));
                return;
            }
            if (!outcome.applied()) {
                source.sendFailure(Component.literal(messages.get(lang, "reload.failed")));
                for (ConfigIssue issue : outcome.errors()) {
                    source.sendFailure(Component.literal(messages.get(lang, "reload.issue", issue)));
                }
                return;
            }
            if (outcome.appliedFields().isEmpty() && outcome.pendingRestartFields().isEmpty()) {
                send(source, messages.get(lang, "reload.unchanged"));
            } else if (!outcome.appliedFields().isEmpty()) {
                send(source, messages.get(lang, "reload.applied", String.join(", ", outcome.appliedFields())));
            }
            if (!outcome.pendingRestartFields().isEmpty()) {
                send(source, messages.get(lang, "reload.pending_restart", String.join(", ", outcome.pendingRestartFields())));
            }
            for (ConfigIssue warning : outcome.warnings()) {
                send(source, messages.get(lang, "reload.warning", warning));
            }
        });
        return 1;
    }

    private static void send(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }
}
