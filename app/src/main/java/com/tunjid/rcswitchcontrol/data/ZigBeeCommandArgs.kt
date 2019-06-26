package com.tunjid.rcswitchcontrol.data

class ZigBeeCommandArgs(val command: String, val args: Array<String>) {

    val isInvalid: Boolean
        get() = args.isEmpty()
}