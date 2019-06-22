package com.tunjid.rcswitchcontrol.nsd.protocols

import android.content.Context
import androidx.annotation.StringRes

import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.data.Payload

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
        null, PING -> Payload.builder().setAction(PING).build()
        RESET -> Payload.builder().setAction(RESET).build()
        else -> Payload.deserialize(input)
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
