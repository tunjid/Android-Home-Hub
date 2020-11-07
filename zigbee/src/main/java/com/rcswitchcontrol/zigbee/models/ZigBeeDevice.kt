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

package com.rcswitchcontrol.zigbee.models

import android.os.Parcel
import android.os.Parcelable
import com.rcswitchcontrol.protocols.models.Device
import com.rcswitchcontrol.zigbee.commands.GroupAddCommand
import com.rcswitchcontrol.zigbee.commands.MembershipAddCommand
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol

data class ZigBeeDevice(
        internal val ieeeAddress: String,
        internal val networkAdress: String,
        internal val endpoint: String,
        override val name: String
) : Parcelable, Device {

    internal val zigBeeId: String get() = "$networkAdress/$endpoint"

    override val key: String = ZigBeeProtocol::class.java.name

    override val diffId
        get() = ieeeAddress

    constructor(parcel: Parcel) : this(
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readString()!!)

    fun commandArgs(input: ZigBeeInput<*>): ZigBeeCommandArgs = input.from(this)

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(ieeeAddress)
        parcel.writeString(networkAdress)
        parcel.writeString(endpoint)
        parcel.writeString(name)
    }

    override fun hashCode(): Int = ieeeAddress.hashCode()

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ZigBeeDevice> {

        override fun createFromParcel(parcel: Parcel): ZigBeeDevice = ZigBeeDevice(parcel)

        override fun newArray(size: Int): Array<ZigBeeDevice?> = arrayOfNulls(size)
    }
}

fun List<ZigBeeDevice>.createGroupSequence(groupName: String): List<ZigBeeCommandArgs> {
    val result = mutableListOf<ZigBeeCommandArgs>()

    val groupId = groupName.hashCode().toString()

    result.add(GroupAddCommand().command.let { ZigBeeCommandArgs(it, arrayOf(it, groupId, groupName)) })
    result.addAll(map { device ->
        MembershipAddCommand().command.let { ZigBeeCommandArgs(it, arrayOf(it, device.zigBeeId, groupId, groupName)) }
    })

    return result
}