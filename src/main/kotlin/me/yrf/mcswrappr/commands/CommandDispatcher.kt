package me.yrf.mcswrappr.commands

import me.yrf.mcswrappr.handlers.ServerWrapper
import me.yrf.mcswrappr.terminal.TermContainer
import me.yrf.mcswrappr.terminal.TerminalManager
import org.jline.reader.LineReader
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PostConstruct

typealias CommandHandler = (String, List<String>, TermContainer) -> Boolean
@Component
class CommandDispatcher {
    private val commandHandlers: MutableMap<String, CommandHandler> = ConcurrentHashMap()
    companion object {
        private val COMMAND_PREFIX = '.'
    }
    fun handle(cmd: String, term: TermContainer): Boolean {
        if (cmd.startsWith(COMMAND_PREFIX)) {
            val str = cmd.substring(1)
            val split = str.split(" ").filter { it.isNotEmpty() }
            if (commandHandlers.containsKey(split[0]))
                return commandHandlers[split[0]]?.invoke(split[0], split.drop(1), term) ?: false
        }

        return false
    }

    fun registerCommand(cmd: String, handler: CommandHandler) {
        commandHandlers.put(cmd, handler)
    }

}

@Component
class CoreCommands(private val cmd: CommandDispatcher,
                   private val wrapper: ServerWrapper,
                   private val ctx: ApplicationContext,
                   private val term: TerminalManager) {
    val logger = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun registerCommands() {
        cmd.registerCommand("start", { _, _, _ ->
            wrapper.startServer()
            true
        })

        cmd.registerCommand("stop", { _, _, _ ->
            wrapper.stopServer()
            true
        })

        cmd.registerCommand("killServer", { _, _, _ ->
            wrapper.killServer()
            true
        })

        cmd.registerCommand("stopWrapper", { _, _, _ ->
            logger.info("[SWRAPPER] Stopping wrapper. Terminating Server.")
            wrapper.stopServer()
            SpringApplication.exit(ctx)
            true
        })

        cmd.registerCommand("clear", { _, _, t ->
            t.inputConnector?.callWidget(LineReader.CLEAR_SCREEN)
            true
        })
    }
}