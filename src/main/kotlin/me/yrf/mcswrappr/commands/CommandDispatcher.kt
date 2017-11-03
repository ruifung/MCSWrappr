package me.yrf.mcswrappr.commands

import jdk.nashorn.internal.ir.Terminal
import me.yrf.mcswrappr.handlers.ServerWrapper
import me.yrf.mcswrappr.terminal.TermContainer
import me.yrf.mcswrappr.terminal.TerminalManager
import org.springframework.boot.SpringApplication
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PostConstruct

typealias CommandHandler = (String, List<String>) -> Boolean
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
                return commandHandlers[split[0]]?.invoke(split[0], split.drop(1)) ?: false
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

    @PostConstruct
    fun registerCommands() {
        cmd.registerCommand("start", { _,_ ->
            wrapper.startServer()
            true
        })

        cmd.registerCommand("stop", { _,_ ->
            wrapper.stopServer()
            true
        })

        cmd.registerCommand("killServer", { _,_ ->
            wrapper.killServer()
            true
        })

        cmd.registerCommand("stopWrapper", {_,_ ->
            term.println("[MCSWRAPPER] Stopping wrapper. Terminating Server.")
            wrapper.stopServer()
            SpringApplication.exit(ctx)
            true
        })
    }
}