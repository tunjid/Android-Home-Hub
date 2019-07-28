/*
 * MIT License
 *
 * Copyright (c) 2019 Adetunji Dahunsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tunjid.rcswitchcontrol.nsd.protocols

import android.content.Context
import androidx.annotation.StringRes
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.data.Payload
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.deserialize
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.serialize
import java.io.Closeable
import java.io.PrintWriter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Class for Server communication with input from client
 *
 *
 * Created by tj.dahunsi on 2/6/17.
 */

abstract class CommsProtocol internal constructor(private val printWriter: PrintWriter) : Closeable {

    val appContext: Context = App.instance

    abstract fun processInput(payload: Payload): Payload

    fun processInput(input: String?): Payload = processInput(when (input) {
        null, PING -> Payload(CommsProtocol::class.java.name).apply { action = PING }
        RESET -> Payload(CommsProtocol::class.java.name).apply { action = RESET }
        else -> input.deserialize(Payload::class)
    })

    fun pushOut(payload: Payload) = printWriter.println(payload.serialize())

    protected fun getString(@StringRes id: Int): String = appContext.getString(id)

    protected fun getString(@StringRes id: Int, vararg args: Any): String = appContext.getString(id, *args)

    companion object {

        const val PING = "Ping"
        internal const val RESET = "Reset"

        val sharedPool: ExecutorService = Executors.newFixedThreadPool(5)
    }

}
