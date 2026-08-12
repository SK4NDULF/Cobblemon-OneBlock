package io.github.sk4ndulf.oneblock.core.wizard

import io.github.sk4ndulf.oneblock.core.OneBlockCore
import io.github.sk4ndulf.oneblock.core.config.MainConfig
import io.github.sk4ndulf.oneblock.core.lang.ServerLang
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

/**
 * Chat-based setup wizard, driven entirely by `/ob setup ...` subcommands so it works
 * identically for players and the server console (no chat listeners, no chat-signing issues).
 *
 * Exactly one session can be active at a time (wizard lock). All state changes happen on the
 * server thread (commands run there), so no synchronization is needed.
 */
object SetupWizard {

    private class Session(val ownerName: String) {
        var index: Int = 0
        var config: MainConfig = OneBlockCore.configManager.mainConfig
        val confirming: Boolean get() = index >= WizardQuestions.ALL.size
    }

    private var session: Session? = null

    // --- command entry points -----------------------------------------------------------

    fun start(source: CommandSourceStack) {
        val active = session
        if (active != null && active.ownerName != source.textName) {
            source.sendFailure(prefixed(ServerLang.msg("oneblock.wizard.locked", active.ownerName)))
            return
        }
        val fresh = Session(source.textName)
        session = fresh
        askCurrent(source, fresh)
    }

    fun answer(source: CommandSourceStack, raw: String) {
        val active = owned(source) ?: return
        if (active.confirming) {
            source.sendFailure(prefixed(ServerLang.msg("oneblock.wizard.already_confirming")))
            return
        }
        val question = WizardQuestions.ALL[active.index]
        when (val result = question.apply(active.config, raw)) {
            is AnswerResult.Error -> {
                source.sendFailure(prefixed(Component.literal(result.message)))
                askCurrent(source, active)
            }
            is AnswerResult.Ok -> {
                active.config = result.config
                advance(source, active)
            }
        }
    }

    fun skip(source: CommandSourceStack) {
        val active = owned(source) ?: return
        if (active.confirming) {
            source.sendFailure(prefixed(ServerLang.msg("oneblock.wizard.already_confirming")))
            return
        }
        advance(source, active)
    }

    fun cancel(source: CommandSourceStack) {
        if (owned(source) == null) return
        session = null
        source.sendSuccess({ prefixed(ServerLang.msg("oneblock.wizard.cancelled")) }, false)
    }

    fun confirm(source: CommandSourceStack) {
        val active = owned(source) ?: return
        if (!active.confirming) {
            askCurrent(source, active)
            return
        }
        OneBlockCore.configManager.updateMain(active.config.copy(setupCompleted = true))
        session = null
        source.sendSuccess({ prefixed(ServerLang.msg("oneblock.wizard.saved").withStyle(ChatFormatting.GREEN)) }, true)
    }

    /** Direct single-value set outside the wizard flow — for console admins and quick fixes. */
    fun setDirect(source: CommandSourceStack, key: String, raw: String) {
        val question = WizardQuestions.byKey(key)
        if (question == null) {
            source.sendFailure(prefixed(ServerLang.msg("oneblock.wizard.set_unknown", key, WizardQuestions.validKeys)))
            return
        }
        when (val result = question.apply(OneBlockCore.configManager.mainConfig, raw)) {
            is AnswerResult.Error -> source.sendFailure(prefixed(Component.literal(result.message)))
            is AnswerResult.Ok -> {
                OneBlockCore.configManager.updateMain(result.config)
                val label = ServerLang.raw(question.labelLangKey)
                val value = question.current(OneBlockCore.configManager.mainConfig)
                source.sendSuccess({ prefixed(ServerLang.msg("oneblock.wizard.set_saved", label, value)) }, true)
            }
        }
    }

    // --- flow helpers -------------------------------------------------------------------

    private fun owned(source: CommandSourceStack): Session? {
        val active = session
        if (active == null) {
            source.sendFailure(prefixed(ServerLang.msg("oneblock.wizard.not_running")))
            return null
        }
        if (active.ownerName != source.textName) {
            source.sendFailure(prefixed(ServerLang.msg("oneblock.wizard.not_owner", active.ownerName)))
            return null
        }
        return active
    }

    private fun advance(source: CommandSourceStack, active: Session) {
        active.index++
        if (active.confirming) {
            showSummary(source, active)
        } else {
            askCurrent(source, active)
        }
    }

    private fun askCurrent(source: CommandSourceStack, active: Session) {
        if (active.confirming) {
            showSummary(source, active)
            return
        }
        val question = WizardQuestions.ALL[active.index]
        val header = ServerLang.msg(
            "oneblock.wizard.question_header", active.index + 1, WizardQuestions.ALL.size,
        ).withStyle(ChatFormatting.GOLD)
        val text = ServerLang.msg(question.questionLangKey).withStyle(ChatFormatting.WHITE)
        source.sendSystemMessage(prefixed(header))
        source.sendSystemMessage(text)

        val current = question.current(active.config)
        val hint: MutableComponent = when (val kind = question.kind) {
            is QuestionKind.WholeNumber ->
                ServerLang.msg("oneblock.wizard.hint.number", kind.min, kind.max, current)
                    .withStyle(ChatFormatting.GRAY)
                    .withStyle { it.withClickEvent(suggest("/ob setup answer $current")) }
            is QuestionKind.Decimal ->
                ServerLang.msg("oneblock.wizard.hint.number", kind.min, kind.max, current)
                    .withStyle(ChatFormatting.GRAY)
                    .withStyle { it.withClickEvent(suggest("/ob setup answer $current")) }
            is QuestionKind.YesNo ->
                ServerLang.msg("oneblock.wizard.hint.bool").withStyle(ChatFormatting.GRAY)
                    .append(" ")
                    .append(button("oneblock.wizard.button.yes", "/ob setup answer yes", ChatFormatting.GREEN))
                    .append(" ")
                    .append(button("oneblock.wizard.button.no", "/ob setup answer no", ChatFormatting.RED))
        }
        hint.append(" ").append(
            ServerLang.msg("oneblock.wizard.hint.skip", current)
                .withStyle(ChatFormatting.DARK_GRAY)
                .withStyle { it.withClickEvent(run("/ob setup skip")) },
        )
        source.sendSystemMessage(hint)
    }

    private fun showSummary(source: CommandSourceStack, active: Session) {
        source.sendSystemMessage(prefixed(ServerLang.msg("oneblock.wizard.summary_header").withStyle(ChatFormatting.GOLD)))
        for (question in WizardQuestions.ALL) {
            val label = ServerLang.raw(question.labelLangKey)
            val value = question.current(active.config)
            source.sendSystemMessage(ServerLang.msg("oneblock.wizard.summary_line", label, value).withStyle(ChatFormatting.WHITE))
        }
        val prompt = ServerLang.msg("oneblock.wizard.summary_confirm").withStyle(ChatFormatting.GOLD)
            .append(" ")
            .append(button("oneblock.wizard.button.confirm", "/ob setup confirm", ChatFormatting.GREEN))
            .append(" ")
            .append(button("oneblock.wizard.button.cancel", "/ob setup cancel", ChatFormatting.RED))
        source.sendSystemMessage(prompt)
    }

    // --- component helpers ---------------------------------------------------------------

    private fun prefixed(component: Component): MutableComponent =
        ServerLang.msg("oneblock.prefix").withStyle(ChatFormatting.AQUA).append(component)

    private fun button(langKey: String, command: String, color: ChatFormatting): MutableComponent =
        ServerLang.msg(langKey).withStyle(color).withStyle { it.withClickEvent(run(command)).withBold(true) }

    private fun run(command: String) = ClickEvent(ClickEvent.Action.RUN_COMMAND, command)

    private fun suggest(command: String) = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)
}
