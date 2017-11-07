package me.yrf.mcswrappr.handlers

import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.spi.DeferredProcessingAware
import me.yrf.mcswrappr.terminal.TerminalManager

class TerminalAppender<E> : AppenderBase<E>() {
    private val layout = PatternLayout()

    var pattern: String?
        get() {
            return layout.pattern
        }
        set(value) {
            layout.pattern = value
        }

    override fun append(eventObject: E) {
        if (!started)
            return

        if (!layout.isStarted)
            layout.start()

        if (eventObject is DeferredProcessingAware)
            eventObject.prepareForDeferredProcessing()

        val str = layout.doLayout(eventObject as ILoggingEvent)
        TerminalManager.println(str.replace(Regex("(\\r\\n|\\r|\\n)"), ""))
    }

    override fun start() {
        super.start()
        layout.context = context
    }
}