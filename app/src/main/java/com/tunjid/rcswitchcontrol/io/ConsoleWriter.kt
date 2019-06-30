package com.tunjid.rcswitchcontrol.io

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.PrintWriter

class ConsoleWriter(private val consumer: (String) -> Unit) : PrintWriter(object : ByteArrayOutputStream() {
    override fun flush() {
        synchronized(this) {
            consumer.invoke(String(toByteArray()))
            reset()
        }
        super.flush()
    }
}, true)