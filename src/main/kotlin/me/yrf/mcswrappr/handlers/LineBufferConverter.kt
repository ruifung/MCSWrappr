package me.yrf.mcswrappr.handlers

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.util.function.Consumer

class LineBufferConverter(cs: Charset, private val output: Consumer<String>) {
    private val dec = cs.newDecoder()
    private val cbuf = ByteBuffer.allocateDirect(2048).asCharBuffer()
    private val line = StringBuilder()

    fun accept(bytes: ByteBuffer, endInput: Boolean) {
        while (bytes.remaining() > 0) {
            cbuf.clear()
            val result = dec.decode(bytes, cbuf, endInput)
            cbuf.flip()
            accept(cbuf)

            if (endInput && line.isNotEmpty())
                output.accept(line.toString())

            //Skip unreadable bytes.
            if (result.isError && bytes.hasRemaining())
                bytes.position(bytes.position() + Math.min(bytes.remaining(), result.length()))

        }
    }

    fun accept(chars: CharBuffer) {
        while (chars.remaining() > 0) {
            val char = chars.get()
            if (char == '\n') {
                if (line.endsWith('\r'))
                    line.setLength(line.length - 1)
                output.accept(line.toString())
                if (line.length > 1024) {
                    line.setLength(1024)
                    line.trimToSize()
                }
                line.setLength(0)
            } else {
                line.append(char)
            }
        }
    }
}