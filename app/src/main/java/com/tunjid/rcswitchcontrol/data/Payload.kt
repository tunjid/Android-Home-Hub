package com.tunjid.rcswitchcontrol.data

import java.io.Serializable
import java.util.*

/**
 * Payload class
 *
 *
 * Created by tj.dahunsi on 2/11/17.
 */

data class Payload(val key: String) : Serializable {

    var data: String? = null
    var action: String? = null
    var response: String? = null

    val commands = LinkedHashSet<String>()

    fun addCommand(command: String) = commands.add(command)

}
