package com.tunjid.rcswitchcontrol.data

import android.graphics.Color
import android.os.Parcel
import android.os.Parcelable
import com.tunjid.androidbootstrap.recyclerview.diff.Differentiable
import com.tunjid.rcswitchcontrol.nsd.protocols.ZigBeeProtocol
import com.tunjid.rcswitchcontrol.zigbee.ColorCommand
import com.tunjid.rcswitchcontrol.zigbee.OffCommand
import com.tunjid.rcswitchcontrol.zigbee.OnCommand
import com.tunjid.rcswitchcontrol.zigbee.RediscoverCommand

data class ZigBeeDevice(
        val ieeeAddress: String,
        val networkAdress: String,
        val endpoint: String,
        override val name: String
) : Parcelable, Device {

    override val key: String = ZigBeeProtocol::class.java.name

    constructor(parcel: Parcel) : this(
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readString()!!)

    fun toggleCommand(state: Boolean) = (if (state) OnCommand() else OffCommand())
            .command
            .let { ZigBeeCommandArgs(it, arrayOf(it, "$networkAdress/$endpoint")) }

    fun rediscoverCommand() = RediscoverCommand().command.let { ZigBeeCommandArgs(it, arrayOf(it, ieeeAddress)) }

    fun colorCommand(color: Int) = ColorCommand().command.let {
        ZigBeeCommandArgs(it, arrayOf(
                it,
                "$networkAdress/$endpoint",
                Color.red(color).toString(),
                Color.green(color).toString(),
                Color.blue(color).toString()
        ))
    }

    override fun getId(): String = ieeeAddress

    override fun areContentsTheSame(other: Differentiable?): Boolean =
            if (other is ZigBeeDevice) other.networkAdress == networkAdress else false

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(ieeeAddress)
        parcel.writeString(networkAdress)
        parcel.writeString(endpoint)
        parcel.writeString(name)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ZigBeeDevice> {

        override fun createFromParcel(parcel: Parcel): ZigBeeDevice = ZigBeeDevice(parcel)

        override fun newArray(size: Int): Array<ZigBeeDevice?> = arrayOfNulls(size)
    }
}