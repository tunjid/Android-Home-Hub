package com.tunjid.rcswitchcontrol.nsd.protocols

import android.content.Context
import androidx.annotation.StringRes

import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.data.Payload
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.deserialize

import java.io.Closeable
import java.io.PrintWriter

/**
 * Class for Server communication with input from client
 *
 *
 * Created by tj.dahunsi on 2/6/17.
 */

abstract class CommsProtocol internal constructor(internal val printWriter: PrintWriter) : Closeable {

    val appContext: Context = App.instance

    fun processInput(input: String?): Payload = processInput(when (input) {
        null, PING -> Payload(CommsProtocol::class.java.name).apply { action = PING }
        RESET -> Payload(CommsProtocol::class.java.name).apply { action = RESET }
        else -> input.deserialize(Payload::class)
    })

    protected fun getString(@StringRes id: Int): String = appContext.getString(id)

    protected fun getString(@StringRes id: Int, vararg args: Any): String =
            appContext.getString(id, *args)

    abstract fun processInput(payload: Payload): Payload

    companion object {

        const val PING = "Ping"
        internal const val RESET = "Reset"
    }

}
