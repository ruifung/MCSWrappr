package me.yrf.mcswrappr.handlers

import com.zaxxer.nuprocess.NuAbstractProcessHandler
import com.zaxxer.nuprocess.NuProcess
import com.zaxxer.nuprocess.NuProcessBuilder
import me.yrf.mcswrappr.WrapperProperties
import me.yrf.mcswrappr.terminal.TerminalManager
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Paths
import java.util.function.Consumer
import javax.annotation.PostConstruct

@Component
class ServerWrapper(val props: WrapperProperties, private val term: TerminalManager) {
    private val logger = LoggerFactory.getLogger(javaClass)

    final var curProc: WrapperProcessHandler? = null
        private set

    private var serverRunning = false

    init {
        Runtime.getRuntime().addShutdownHook(Thread(null, { curProc?.stop() }, "SWShutdownHook"))
    }

    fun isAlive(): Boolean {
        return curProc?.isAlive() == true
    }

    fun startServer() {
        if (curProc?.isAlive() == true)
            return
        logger.info("[WRAPPER] Starting server")
        val cmd = mutableListOf<String>()
        cmd.add(props.javaPath)
        cmd.add("-Xms${props.minMemory}")
        cmd.add("-Xmx${props.maxMemory}")
        cmd.addAll(props.jvmArgs)
        cmd.add("-jar")
        cmd.add(props.jarPath)
        cmd.addAll(props.jarArgs)
        val dir = Paths.get(props.jarPath).toAbsolutePath().parent
        val pb = NuProcessBuilder(cmd)
        val handler = WrapperProcessHandler(term::println)
        pb.setProcessListener(handler)
        pb.setCwd(dir)
        pb.start()
        curProc = handler
        serverRunning = true

    }

    fun write(str: String) {
        curProc?.write(Charset.defaultCharset().encode(str))
    }

    fun stopServer() {
        if (curProc?.isAlive() != true)
            return
        logger.info("[WRAPPER] Stopping server")
        serverRunning = false
        runCommand("stop")
        val stopped = System.currentTimeMillis()
        while (isAlive()) {
            if (System.currentTimeMillis() - stopped > 60000) {
                logger.warn("[WRAPPER] Failed to stop within 60s, killing.")
                killServer()
            } else
                Thread.sleep(100)
        }
    }

    fun killServer() {
        if (curProc?.isAlive() != true)
            return
        logger.info("[WRAPPER] Killing server")
        serverRunning = false
        curProc?.kill()
        curProc = null
    }

    fun runCommand(cmd: String) {
        val str = if (!cmd.endsWith('\n'))
            cmd + '\n'
        else
            cmd
        curProc?.write(Charset.defaultCharset().encode(str))
    }

    @PostConstruct
    fun init() {
        term.addInputHandler(this::runCommand)
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
        if (serverRunning) {
            startServer()
        }
    }

    @EventListener
    fun onReadyEvent(e: ApplicationReadyEvent) {
        startServer()
    }

    inner class WrapperProcessHandler(private val outputLines: (String) -> Unit) : NuAbstractProcessHandler() {
        private var proc: NuProcess? = null
        private var alive = true

        private val stdout = LineBufferConverter(Charset.defaultCharset(), Consumer {
            outputLines(it)
        })
        private val stderr = LineBufferConverter(Charset.defaultCharset(), Consumer {
            outputLines(it)
        })

        override fun onExit(statusCode: Int) {
            super.onExit(statusCode)
            alive = false
        }

        override fun onStart(nuProcess: NuProcess?) {
            this.proc = nuProcess
            alive = true
        }

        fun kill() {
            proc?.destroy(true)
        }

        fun stop() {
            proc?.destroy(false)
        }

        fun isAlive(): Boolean {
            return alive && proc?.isRunning == true
        }

        override fun onStderr(buffer: ByteBuffer?, closed: Boolean) {
            buffer?.let { stderr.accept(it, closed) }
        }

        override fun onStdout(buffer: ByteBuffer?, closed: Boolean) {
            buffer?.let { stdout.accept(it, closed) }
        }

        fun write(buf: ByteBuffer) {
            proc?.writeStdin(buf)
        }

    }

}