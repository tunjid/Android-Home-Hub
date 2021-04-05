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

package com.tunjid.rcswitchcontrol.control

import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.models.Payload
import com.tunjid.androidx.recyclerview.diff.Diffable
import com.tunjid.rcswitchcontrol.common.Writable

@kotlinx.serialization.Serializable
sealed class Record : Diffable, Writable {
    abstract val key: CommsProtocol.Key
    abstract val entry: String

    override val diffId: String
        get() = key.value

    @kotlinx.serialization.Serializable
    data class Command(
        override val key: CommsProtocol.Key,
        val command: CommsProtocol.Action
    ) : Record() {
        override val entry: String = command.value
    }

    @kotlinx.serialization.Serializable
    data class Response(
        override val key: CommsProtocol.Key,
        override val entry: String,
    ) : Record()
}

val Record.Command.payload
    get() = Payload(key = key, action = command)