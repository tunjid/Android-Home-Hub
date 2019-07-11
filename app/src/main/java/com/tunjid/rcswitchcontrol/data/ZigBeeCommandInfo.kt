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

package com.tunjid.rcswitchcontrol.data

import android.os.Parcel
import android.os.Parcelable
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.converter

class ZigBeeCommandInfo(
        val command: String,
        private val description: String,
        private val syntax: String,
        private val help: String
) : Parcelable {

    val entries: List<Entry>

    init {
        entries = syntax.removePrefix(command)
                .split(" ")
                .toMutableList()
                .apply { if (contains(command)) remove(command) }
                .filter { it.isNotBlank() }
                .map { Entry(it, "") }
    }

    constructor(parcel: Parcel) : this(
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readString()!!)

    fun serialize(): String = converter.toJson(this)

    fun toArgs(): ZigBeeCommandArgs {
        val args = entries.map { it.value }.filter(String::isNotBlank).toMutableList()
        args.add(0, command)

        return ZigBeeCommandArgs(command, args.toTypedArray())
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(command)
        parcel.writeString(description)
        parcel.writeString(syntax)
        parcel.writeString(help)
    }

    override fun describeContents(): Int = 0

    class Entry(var key: String, var value: String)

    companion object CREATOR : Parcelable.Creator<ZigBeeCommandInfo> {

        override fun createFromParcel(parcel: Parcel): ZigBeeCommandInfo = ZigBeeCommandInfo(parcel)

        override fun newArray(size: Int): Array<ZigBeeCommandInfo?> = arrayOfNulls(size)
    }
}