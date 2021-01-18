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

package com.tunjid.rcswitchcontrol.common

import android.util.Log
import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface Writable

val converter: Json = Json { isLenient = true }

inline fun <reified T : Writable> T.serialize(): String = converter.encodeToString(this).replace("\n", "").trim()

inline fun <reified T : Writable> List<T>.serializeList(): String = converter.encodeToString(this).replace("\n", "").trim()

inline fun <reified T : Writable> String.deserialize(kClass: KClass<T>): T = try {
    converter.decodeFromString(this)
} catch (exception: Exception) {
    Log.i("TEST", "Error deserializing ${kClass.java.name}\nvalue:$this\n", exception)
    throw exception
}

inline fun <reified T : Any> T.serialize(serializer: KSerializer<T>): String = converter.encodeToString(serializer,this).replace("\n", "").trim()

inline fun <reified T : Any> String.deserialize(serializer: KSerializer<T>): T = try {
    converter.decodeFromString(serializer,this)
} catch (exception: Exception) {
    Log.i("TEST", "Error deserializing ${T::class.java.name}\nvalue:$this\n", exception)
    throw exception
}

inline fun <reified T : Writable> String.deserializeList(kClass: KClass<T>): List<T> = converter.decodeFromString(this)
