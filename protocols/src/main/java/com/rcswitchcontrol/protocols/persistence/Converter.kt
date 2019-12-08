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

package com.rcswitchcontrol.protocols.persistence

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.reflect.KClass

class Converter<out T : Any> internal constructor(private val kClass: KClass<T>) {

    //     fun serialize(item: T): String = converter.toJson(item)
//
//     fun serializeList(items: List<out T>): String = converter.toJson(items)
//
    private fun deserialize(serialized: String): T = converter.fromJson(serialized, kClass.java)

    private fun deserializeList(serialized: String): List<T> = converter.fromJson(serialized, TypeToken.getParameterized(List::class.java, kClass.java).type)

    companion object {

        val converter = Gson()
        private val map = mutableMapOf<KClass<*>, Converter<*>>()

        fun register(clazz: KClass<*>) {
            map[clazz] = Converter(clazz)
        }

        fun <T : Any> T.serialize(): String = converter.toJson(this)

        fun <T : Any> List<T>.serializeList(): String = converter.toJson(this)

        fun <T : Any> String.deserialize(kClass: KClass<T>): T = converter(kClass).deserialize(this)

        fun <T : Any> String.deserializeList(kClass: KClass<T>): List<T> = converter(kClass).deserializeList(this)

        @Suppress("UNCHECKED_CAST")
        private fun <T : Any> converter(kClass: KClass<T>): Converter<T> =
                map[kClass] as? Converter<T>
                        ?: throw IllegalArgumentException("This class must be registered to be converted first")
    }
}