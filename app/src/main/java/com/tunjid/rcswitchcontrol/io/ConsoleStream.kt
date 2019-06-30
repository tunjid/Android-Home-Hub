package com.tunjid.rcswitchcontrol.io

import java.io.ByteArrayOutputStream
import java.io.PrintStream

class ConsoleStream(private val consumer: (String) -> Unit) : PrintStream(object : ByteArrayOutputStream() {
    override fun flush() {
        synchronized(this) {
            consumer.invoke(String(toByteArray()))
            reset()
        }
        super.flush()
    }
}, true)