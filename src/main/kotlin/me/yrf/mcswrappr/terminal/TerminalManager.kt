package me.yrf.mcswrappr.terminal

import com.google.common.collect.EvictingQueue
import com.google.common.collect.Queues
import me.yrf.mcswrappr.WrapperProperties
import me.yrf.mcswrappr.commands.CommandDispatcher
import me.yrf.mcswrappr.discardExceptions
import org.jline.reader.LineReaderBuilder
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.springframework.context.annotation.Scope
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PostConstruct
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

@Component
@Scope("singleton")
class TerminalManager(props: WrapperProperties, private val commandDispatcher: CommandDispatcher) {
    private final val systemTerminal: TermContainer = TermContainer(TerminalBuilder.terminal())
    private final val remoteTerminals: MutableList<TermContainer> = Collections.synchronizedList(ArrayList())
    private final val inputConnectors: MutableMap<Terminal, InputConnector> = ConcurrentHashMap()
    private final val inputDestinations: MutableList<(String) -> Unit> = Collections.synchronizedList(ArrayList())
    private final val playbackQueue: Queue<String> = EvictingQueue.create<String>(props.consoleLineBuffer)
    private final val syncronizedQueue: Queue<String> = Queues.synchronizedQueue(playbackQueue)

    private final val charEncoder = Charset.defaultCharset().newEncoder()

    companion object {
        private val queue = EvictingQueue.create<String>(50)
        private var instance: TerminalManager? = null
            set(value) {
                if (field == null && value != null) {
                    queue.forEach(value::println)
                    queue.clear()
                }
                field = value
            }

        fun println(str: String) {
            if (instance != null)
                instance?.println(str)
            else
                queue.offer(str)
        }
    }

    /**
     * Create a remote terminal from the given input and output streams.
     */
    fun createRemoteTerminal(stdin: InputStream, stdout: OutputStream, termination: () -> Unit): Terminal {
        val term = TerminalBuilder.builder()
                .system(false)
                .streams(stdin, stdout)
                .build()

        val cont = TermContainer(term, termination)
        val conn = InputConnector(cont, true)
        inputConnectors.put(term, conn)
        remoteTerminals.add(cont)
        conn.start()
        thread(name = "Playback Queue Replay Thread") {
            val writer = term.writer()
            playbackQueue.toTypedArray().forEach(writer::println) //Clone it to write.
            writer.flush()
        }
        return term
    }

    /**
     * Remove a remote terminal.
     */
    fun removeRemoteTerminal(term: Terminal) {
        if (term != systemTerminal.term) {
            remoteTerminals.removeIf { it.term == term }
            inputConnectors.remove(term)
            discardExceptions { term.close() }
        } else
            throw IllegalArgumentException("Attempted to remove System Terminal")
    }

    fun addInputHandler(fn: (String) -> Unit) {
        this.inputDestinations.add(fn)
    }

    /**
     * Initialize the terminal manager.
     */
    @PostConstruct
    fun init() {
        instance = this
        val conn = InputConnector(systemTerminal)
        inputConnectors.put(systemTerminal.term, conn)
        conn.start()
    }

    @Async
    fun println(s: String) {
        systemTerminal.term.writer().println(s)
        remoteTerminals.map { it.term }.forEach {
            it.writer().println(s)
            it.writer().flush()
        }
        systemTerminal.term.writer().flush()
        syncronizedQueue.offer(s)
    }

    fun write(c: CharBuffer) {
        write(charEncoder.encode(c))
    }

    fun write(b: ByteBuffer) {
        systemTerminal.outputChannel.write(b)
        remoteTerminals.map { it.outputChannel }.forEach {
            b.rewind()
            it.write(b)
        }
    }

    inner class InputConnector(private val term: TermContainer, private val isRemote: Boolean = false)
        : Thread(null, null, "Input Connector Thread") {
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
                        try {
                            dst(line)
                        } catch (ex: Exception) {
                            iter.remove()
                        }
                    }
                } catch (ex: Exception) {
                    if (isRemote)
                        running = false
                }
            }
        }
    }
}

class TermContainer(val term: Terminal, val terminationCallback: () -> Unit = {}) {
    val outputChannel: WritableByteChannel by lazy {
        Channels.newChannel(term.output())
    }

    val inputChannel: ReadableByteChannel by lazy {
        Channels.newChannel(term.input())
    }
}
