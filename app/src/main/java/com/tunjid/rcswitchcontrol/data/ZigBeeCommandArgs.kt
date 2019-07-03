package com.tunjid.rcswitchcontrol.data

import com.tunjid.rcswitchcontrol.nsd.protocols.ZigBeeProtocol

class ZigBeeCommandArgs(val command: String, val args: Array<String>) {

    val key: String = ZigBeeProtocol::class.java.name

    val isInvalid: Boolean
        get() = args.isEmpty()
}