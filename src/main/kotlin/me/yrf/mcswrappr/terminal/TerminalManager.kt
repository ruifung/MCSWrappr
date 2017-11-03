package me.yrf.mcswrappr.terminal

import com.google.common.collect.EvictingQueue
import com.google.common.collect.Queues
import me.yrf.mcswrappr.WrapperProperties
import me.yrf.mcswrappr.commands.CommandDispatcher
import me.yrf.mcswrappr.discardExceptions
import org.jline.reader.LineReaderBuilder
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.springframework.stereotype.Component
import java.io.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PostConstruct
import kotlin.collections.ArrayList

@Component
class TerminalManager(props: WrapperProperties, private val commandDispatcher: CommandDispatcher) {
    val systemTerminal: TermContainer = TermContainer(TerminalBuilder.terminal())
    val remoteTerminals: MutableList<TermContainer> = Collections.synchronizedList(ArrayList())
    val outputConnectors: MutableList<StringOutputConnector> = Collections.synchronizedList(ArrayList())
    val inputConnectors: MutableMap<Terminal, InputConnector> = ConcurrentHashMap()
    val inputDestinations: MutableList<PrintStream> = Collections.synchronizedList(ArrayList())
    val playbackQueue = Queues.synchronizedQueue(EvictingQueue.create<String>(props.consoleLineBuffer))

    /**
     * Create a remote terminal from the given input and output streams.
     */
    fun createRemoteTerminal(stdin: InputStream, stdout: OutputStream, termination: () -> Unit): Terminal {
        val term = TerminalBuilder.builder()
                .system(false)
                .streams(stdin, stdout)
                .build()

        val cont = TermContainer(term, termination)
        val conn = InputConnector(cont)
        inputConnectors.put(term, conn)
        remoteTerminals.add(cont)
        playbackQueue.forEach { term.writer().println(it) }
        conn.start()
        return term;
    }

    /**
     * Remove a remote terminal.
     */
    fun removeRemoteTerminal(term: Terminal) {
        if (term != systemTerminal.term) {
            remoteTerminals.removeIf{it.term == term}
            inputConnectors.remove(term)
            discardExceptions { term.close() }
        } else
            throw IllegalArgumentException("Attempted to remove System Terminal")
    }

    /**
     * Initialize the terminal manager.
     */
    @PostConstruct
    fun init() {
        val conn = InputConnector(systemTerminal)
        inputConnectors.put(systemTerminal.term, conn)
        conn.start()
    }

    /**
     * Connect the InputStream to output of ALL terminals
     */
    fun connectToOutput(stream: InputStream) {
        val conn = StringOutputConnector(stream)
        outputConnectors.add(conn)
        conn.start()
    }

    /**
     * Connect the OutputStream to input of ALL terminals.
     */
    fun connectToInput(stream: OutputStream) {
        inputDestinations.add(PrintStream(stream, true))
    }

    fun println(s: String) {
        playbackQueue.add(s)
        systemTerminal.term.writer().println(s)
        systemTerminal.term.writer().flush()
        remoteTerminals.map { it.term }.forEach {
            it.writer().println(s)
            it.writer().flush()
        }
    }

    inner class InputConnector(private val term: TermContainer) : Thread() {
        private val lr = LineReaderBuilder.builder()
                .terminal(term.term)
                .build()

        init {
            isDaemon = true
        }

        override fun run() {
            var running = true
            while (running && !isInterrupted) {
                try {
                    val line = lr.readLine()
                    val isWrapperCmd = commandDispatcher.handle(line, term)
                    if (isWrapperCmd)
                        continue
                    val iter = inputDestinations.iterator()
                    for (dst in iter) {
                        dst.println(line)
                        if (dst.checkError())
                            iter.remove()
                    }
                } catch (ex: Exception) {
                    running = false
                }
            }
        }
    }

    inner class StringOutputConnector(private val src: InputStream, name: String = "Terminal Output Connector") : Thread(null, null, name) {
        init {
            isDaemon = true
        }

        override fun run() {
            val br = BufferedReader(InputStreamReader(src))
            var running = true
            while (running && !isInterrupted) {
                try {
                    val s = br.readLine()
                    println(s)
                } catch (ex: Exception) {
                    running = false
                }
            }
            outputConnectors.remove(this)
        }
    }
}

class TermContainer(val term: Terminal, val terminationCallback: () -> Unit = {})
