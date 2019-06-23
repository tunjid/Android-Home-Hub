package com.tunjid.rcswitchcontrol.data

import android.os.Parcel
import android.os.Parcelable
import com.tunjid.rcswitchcontrol.data.persistence.Deserializer
import com.tunjid.rcswitchcontrol.data.persistence.Deserializer.Companion.gson

class ZigBeeCommandInfo(
        val command: String,
        private val description: String,
        private val syntax: String,
        private val help: String
) : Parcelable {

    val entries: List<Entry>

    init {
        entries = syntax.split(" ")
                .toMutableList()
                .apply { if (contains(command)) remove(command) }
                .map { Entry(it, "") }
    }

    constructor(parcel: Parcel) : this(
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readString()!!)

    fun serialize(): String = gson.toJson(this)

    fun toArgs(): ZigBeeCommandArgs {
        val args = entries.map { it.value }.toMutableList()
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

    companion object CREATOR :
            Deserializer<ZigBeeCommandInfo>,
            Parcelable.Creator<ZigBeeCommandInfo> {

        override fun deserialize(serialized: String): ZigBeeCommandInfo = gson.fromJson(serialized, ZigBeeCommandInfo::class.java)

        override fun createFromParcel(parcel: Parcel): ZigBeeCommandInfo = ZigBeeCommandInfo(parcel)

        override fun newArray(size: Int): Array<ZigBeeCommandInfo?> = arrayOfNulls(size)
    }
}