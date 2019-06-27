package com.tunjid.rcswitchcontrol.data.persistence

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tunjid.rcswitchcontrol.data.*
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
        private val payload = Converter(Payload::class)
        private val rcSwitch = Converter(RcSwitch::class)
        private val zigBeeDevice = Converter(ZigBeeDevice::class)
        private val zigBeeCommandArgs = Converter(ZigBeeCommandArgs::class)
        private val zigBeeCommandInfo = Converter(ZigBeeCommandInfo::class)

        fun <T : Any> T.serialize(): String = converter.toJson(this)

        fun <T : Any> List<T>.serializeList(): String = converter.toJson(this)

        fun <T : Any> String.deserialize(kClass: KClass<T>): T = converter(kClass).deserialize(this)

        fun <T : Any> String.deserializeList(kClass: KClass<T>): List<T> = converter(kClass).deserializeList(this)

        private fun <T : Any> converter(kClass: KClass<T>): Converter<T> = when (kClass) {
            Payload::class -> payload
            RcSwitch::class -> rcSwitch
            ZigBeeDevice::class -> zigBeeDevice
            ZigBeeCommandArgs::class -> zigBeeCommandArgs
            ZigBeeCommandInfo::class -> zigBeeCommandInfo
            else -> throw IllegalArgumentException("Invalid class")
        } as Converter<T>
    }
}