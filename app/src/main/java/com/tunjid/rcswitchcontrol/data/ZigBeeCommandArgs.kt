package com.tunjid.rcswitchcontrol.data

import com.tunjid.rcswitchcontrol.data.persistence.Deserializer

class ZigBeeCommandArgs(val command: String, val args: Array<String>) {

    val isInvalid: Boolean
        get() = args.isEmpty()

    fun serialize(): String = Deserializer.gson.toJson(this)

    companion object : Deserializer<ZigBeeCommandArgs> {

        override fun deserialize(serialized: String): ZigBeeCommandArgs = Deserializer.gson.fromJson(serialized, ZigBeeCommandArgs::class.java)

    }
}