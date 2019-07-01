package com.tunjid.rcswitchcontrol.io

import java.io.ByteArrayOutputStream

class FlushNotifyingOutputStream(private val consumer: (String) -> Unit) : ByteArrayOutputStream() {
    override fun flush() {
        synchronized(this) {
            consumer.invoke(String(toByteArray()))
            reset()
        }
        super.flush()
    }
}