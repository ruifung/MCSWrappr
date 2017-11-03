package me.yrf.mcswrappr.handlers

import me.yrf.mcswrappr.WrapperProperties
import me.yrf.mcswrappr.terminal.TerminalManager
import org.slf4j.LoggerFactory
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.PrintWriter
import java.nio.file.Paths
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@Component
class ServerWrapper(val props: WrapperProperties, private val term: TerminalManager) {

    final var curProc: Process? = null
        private set
    final var commandPrinter: PrintWriter? = null
        private set

    private var stopCmdIssued = false

    fun isAlive(): Boolean {
        return curProc?.isAlive == true
    }

    fun startServer() {
        if (curProc?.isAlive == true)
            return
        term.println("[WRAPPER] Starting server")
        stopCmdIssued = false
        val jvmArgs = props.jvmArgs.joinToString(" ")
        val jarArgs = props.jarArgs.joinToString(" ")
        val cmd = "${props.javaPath} -Xms${props.minMemory} -Xmx${props.maxMemory} $jvmArgs -jar ${props.jarPath} $jarArgs"

        val dir = Paths.get(props.jarPath).toAbsolutePath().parent.toFile()
        val proc = ProcessBuilder()
                .directory(dir)
                .command(cmd.split(" "))
                .redirectErrorStream(true)
                .start()

        term.connectToInput(proc.outputStream)
        term.connectToOutput(proc.inputStream)

        try {
            curProc?.inputStream?.close()
            curProc?.outputStream?.close()
            commandPrinter?.close()
        } catch (e: Exception) {
            //IGNORE!
        }

        curProc = proc
        commandPrinter = PrintWriter(proc.outputStream)
    }

    fun stopServer() {
        if (curProc?.isAlive != true)
            return
        term.println("[WRAPPER] Stopping server")
        stopCmdIssued = true
        runCommand("stop")

        val stopped = System.currentTimeMillis()
        while(isAlive()) {
            if (System.currentTimeMillis() - stopped > 60000) {
                term.println("[WRAPPER] Failed to stop within 60s, killing.")
                killServer()
            } else
                Thread.sleep(100)
        }
    }

    fun killServer() {
        if (curProc?.isAlive != true)
            return
        term.println("[WRAPPER] Killing server")
        stopCmdIssued = true
        curProc?.destroyForcibly()
        curProc = null
        commandPrinter = null
    }

    fun runCommand(cmd: String) {
        commandPrinter?.println(cmd)
        commandPrinter?.flush()
    }

    @PostConstruct
    fun init() {
        startServer()
    }

    @EventListener
    fun onDie(event: ContextClosedEvent) {
        stopServer()
    }

    @Scheduled(fixedDelay = 10000)
    fun checkServer() {
        if (!isAlive())
            performRestart()
    }

    fun performRestart() {
        if (!stopCmdIssued) {
            startServer()
        }
    }

}