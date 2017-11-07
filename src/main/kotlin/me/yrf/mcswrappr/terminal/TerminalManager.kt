package me.yrf.mcswrappr.terminal

import com.google.common.collect.EvictingQueue
import com.google.common.collect.Queues
import me.yrf.mcswrappr.WrapperProperties
import me.yrf.mcswrappr.commands.CommandDispatcher
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.impl.history.DefaultHistory
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.slf4j.LoggerFactory
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
import javax.annotation.PostConstruct
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

@Component
@Scope("singleton")
class TerminalManager(props: WrapperProperties, private val commandDispatcher: CommandDispatcher) {
    private final val logger = LoggerFactory.getLogger(javaClass)
    private final val systemTerminal: TermContainer = TermContainer(TerminalBuilder.terminal())
    private final val remoteTerminals: MutableList<TermContainer> = Collections.synchronizedList(ArrayList())
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
        cont.inputConnector = InputConnector(cont, true)
        remoteTerminals.add(cont)
        cont.inputConnector?.start()
        thread(name = "Playback Queue Replay Thread") {
            val writer = term.writer()
            cont.inputConnector?.callWidget(LineReader.CLEAR)
            playbackQueue.toTypedArray().forEach(writer::println) //Clone it to write.
            cont.inputConnector?.callWidget(LineReader.REDRAW_LINE)
            cont.inputConnector?.callWidget(LineReader.REDISPLAY)
            writer.flush()
        }
        return term
    }

    /**
     * Remove a remote terminal.
     */
    fun removeRemoteTerminal(term: Terminal) {
        if (term != systemTerminal.term) {
            val cont = remoteTerminals.find { it.term == term }
            cont?.let {
                remoteTerminals.remove(it)
                it.inputConnector?.interrupt()
            }
            try {
                term.close()
            } catch (ex: Exception) {
                logger.error("Error closing terminal", ex)
            }
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
        systemTerminal.inputConnector = InputConnector(systemTerminal)
        systemTerminal.inputConnector?.start()
    }

    @Async
    fun println(s: String) {
        val conn = systemTerminal.inputConnector
        conn?.callWidget(LineReader.CLEAR)
        systemTerminal.term.writer().println(s)
        conn?.callWidget(LineReader.REDRAW_LINE)
        conn?.callWidget(LineReader.REDISPLAY)
        systemTerminal.term.writer().flush()
        remoteTerminals.forEach {
            val theConnector = it.inputConnector
            theConnector?.callWidget(LineReader.CLEAR)
            it.term.writer().println(s)
            theConnector?.callWidget(LineReader.REDRAW_LINE)
            theConnector?.callWidget(LineReader.REDISPLAY)
            it.term.writer().flush()
        }
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
                .history(DefaultHistory())
                .build()

        init {
            isDaemon = true
        }

        fun callWidget(str: String) {
            lr.callWidget(str)
        }

        override fun run() {
            var running = true
            while (running && !isInterrupted) {
                try {
                    val line = lr.readLine("mc>")
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

    var inputConnector: TerminalManager.InputConnector? = null
}
