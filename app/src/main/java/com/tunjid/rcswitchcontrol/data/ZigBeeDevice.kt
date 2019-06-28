package com.tunjid.rcswitchcontrol.data

import android.os.Parcel
import android.os.Parcelable

class ZigBeeDevice(
        val ieeeAddress: String,
        val networkAdress: String,
        val endpoint: String,
        override val name: String
) : Parcelable, Device {

    constructor(parcel: Parcel) : this(
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readString()!!)

    fun toggleCommand(state: Boolean) = (if (state) "on" else "off").let { ZigBeeCommandArgs(it, arrayOf(it, "$networkAdress/$endpoint")) }

    fun rediscoverCommand() = "rediscover".let { ZigBeeCommandArgs(it, arrayOf(it, ieeeAddress)) }

    override fun getId(): String = ieeeAddress

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(ieeeAddress)
        parcel.writeString(networkAdress)
        parcel.writeString(endpoint)
        parcel.writeString(name)
    }

    override fun describeContents(): Int = 0
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ZigBeeDevice

        if (ieeeAddress != other.ieeeAddress) return false

        return true
    }

    override fun hashCode(): Int = ieeeAddress.hashCode()

    companion object CREATOR : Parcelable.Creator<ZigBeeDevice> {

        override fun createFromParcel(parcel: Parcel): ZigBeeDevice = ZigBeeDevice(parcel)

        override fun newArray(size: Int): Array<ZigBeeDevice?> = arrayOfNulls(size)
    }
}