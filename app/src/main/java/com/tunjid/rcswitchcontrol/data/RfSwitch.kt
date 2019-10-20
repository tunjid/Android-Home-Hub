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
import android.util.Base64
import androidx.annotation.StringDef
import com.tunjid.rcswitchcontrol.nsd.protocols.SerialRFProtocol

/**
 * A model representing an RF switch
 *
 *
 * Created by tj.dahunsi on 3/11/17.
 */

class RfSwitch() : Parcelable, Device {

    override var name: String = "Switch"
    override val key: String
        get() = SerialRFProtocol::class.java.name

    private var bitLength: Byte = 0
    private var protocol: Byte = 0

    private var pulseLength = ByteArray(4)
    private var onCode = ByteArray(4)
    private var offCode = ByteArray(4)

    override val diffId
        get() = "$onCode-$offCode"

    @Retention(AnnotationRetention.SOURCE)
    @StringDef(ON_CODE, OFF_CODE)
    internal annotation class SwitchCode {}

    private constructor(`in`: Parcel) : this() {
        name = `in`.readString()!!
        protocol = `in`.readByte()
        bitLength = `in`.readByte()
        onCode = `in`.createByteArray()!!
        offCode = `in`.createByteArray()!!
        pulseLength = `in`.createByteArray()!!
    }

    private fun getTransmission(state: Boolean): ByteArray {
        val transmission = ByteArray(10)

        System.arraycopy(if (state) onCode else offCode, 0, transmission, 0, onCode.size)
        System.arraycopy(pulseLength, 0, transmission, 4, pulseLength.size)
        transmission[8] = bitLength
        transmission[9] = protocol

        return transmission
    }

    fun getEncodedTransmission(state: Boolean): String =
            Base64.encodeToString(getTransmission(state), Base64.DEFAULT)

    // Equals considers the code only, not the name
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val rcSwitch = other as RfSwitch? ?: return false

        return onCode.contentEquals(rcSwitch.onCode) && offCode.contentEquals(rcSwitch.offCode)
    }

    override fun hashCode(): Int {
        var result = onCode.contentHashCode()
        result = 31 * result + offCode.contentHashCode()
        return result
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(name)
        dest.writeByte(protocol)
        dest.writeByte(bitLength)
        dest.writeByteArray(onCode)
        dest.writeByteArray(offCode)
        dest.writeByteArray(pulseLength)
    }

    class SwitchCreator {
        @SwitchCode
        @get:SwitchCode
        var state: String = ON_CODE
            internal set

        private lateinit var rfSwitch: RfSwitch

        fun withOnCode(code: ByteArray) {
            state = OFF_CODE

            rfSwitch = RfSwitch()

            rfSwitch.bitLength = code[8]
            rfSwitch.protocol = code[9]

            System.arraycopy(code, 0, rfSwitch.onCode, 0, 4)
            System.arraycopy(code, 4, rfSwitch.pulseLength, 0, 4)
        }

        fun withOffCode(code: ByteArray): RfSwitch {
            state = ON_CODE
            System.arraycopy(code, 0, rfSwitch.offCode, 0, 4)
            return rfSwitch
        }
    }

    companion object {
        // Shared preference key
        const val SWITCH_PREFS = "SwitchPrefs"

        const val ON_CODE = "on"
        const val OFF_CODE = "off"

        @JvmField
        @Suppress("unused")
        val CREATOR: Parcelable.Creator<RfSwitch> = object : Parcelable.Creator<RfSwitch> {
            override fun createFromParcel(`in`: Parcel): RfSwitch = RfSwitch(`in`)

            override fun newArray(size: Int): Array<RfSwitch?> = arrayOfNulls(size)
        }
    }
}
