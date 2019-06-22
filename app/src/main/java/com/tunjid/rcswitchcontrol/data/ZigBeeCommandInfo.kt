package com.tunjid.rcswitchcontrol.data

import com.tunjid.rcswitchcontrol.data.persistence.Deserializer
import com.tunjid.rcswitchcontrol.data.persistence.Deserializer.Companion.gson

class ZigBeeCommandInfo(
        val command: String,
        val description: String,
        val syntax: String,
        val help: String
) {
    val argKeys = syntax.split(" ")

    fun hasNoArguments() = syntax.isEmpty()

    fun serialize(): String = gson.toJson(this)

    companion object : Deserializer<ZigBeeCommandInfo> {

        override fun deserialize(serialized: String): ZigBeeCommandInfo = gson.fromJson(serialized, ZigBeeCommandInfo::class.java)

    }
}