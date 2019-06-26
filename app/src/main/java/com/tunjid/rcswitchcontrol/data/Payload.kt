package com.tunjid.rcswitchcontrol.data

import java.io.Serializable
import java.util.*

/**
 * Payload class
 *
 *
 * Created by tj.dahunsi on 2/11/17.
 */

class Payload : Serializable {

    var key: String? = null
        private set
    var data: String? = null
        private set
    var action: String? = null
        private set
    var response: String? = null
        private set

    val commands = LinkedHashSet<String>()

    class Builder {
        private var payload = Payload()

        fun setKey(key: String): Builder {
            payload.key = key
            return this
        }

        fun setData(data: String): Builder {
            payload.data = data
            return this
        }

        fun setAction(action: String): Builder {
            payload.action = action
            return this
        }

        fun setResponse(response: String): Builder {
            payload.response = response
            return this
        }

        fun addCommand(command: String): Builder {
            payload.commands.add(command)
            return this
        }

        fun build(): Payload {
            return payload
        }
    }

    companion object {

        fun builder(): Builder = Builder()
    }

}
