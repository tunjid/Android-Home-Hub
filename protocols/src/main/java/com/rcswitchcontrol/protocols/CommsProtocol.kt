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

package com.rcswitchcontrol.protocols

import android.os.Parcelable
import com.rcswitchcontrol.protocols.models.Payload
import com.tunjid.rcswitchcontrol.common.deserialize
import com.tunjid.rcswitchcontrol.common.serialize
import java.io.Closeable
import java.io.PrintWriter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.parcelize.Parcelize

/**
 * Class for Server communication with input from client
 *
 *
 * Created by tj.dahunsi on 2/6/17.
 */

interface CommsProtocol : Closeable {

    val printWriter: PrintWriter

    fun processInput(payload: Payload): Payload

    fun processInput(input: String?): Payload = processInput(when (input) {
        null, pingAction.value -> Payload(key = key, action = pingAction)
        resetAction.value -> Payload(key = key, action = resetAction)
        else -> input.deserialize(Payload::class)
    })

    fun pushOut(payload: Payload) = printWriter.println(payload.serialize())

    @Parcelize
    data class Key(val value: String): Parcelable

    data class Action(val value: String)

    companion object {
        val key get() = Key(CommsProtocol::class.java.name)
        val pingAction get() = Action("Ping")
        val resetAction get() = Action("Reset")
        val sharedPool: ExecutorService = Executors.newFixedThreadPool(5)
    }
}

val String.asAction get() = CommsProtocol.Action(this)

@Parcelize
data class Name(
    val id: String,
    val key: CommsProtocol.Key,
    val value: String
): Parcelable

val Name.renamePayload get() = Payload(
    key = key,
    action = CommonDeviceActions.renameAction,
    data = serialize()
)