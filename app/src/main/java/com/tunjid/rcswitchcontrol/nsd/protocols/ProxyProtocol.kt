package com.tunjid.rcswitchcontrol.nsd.protocols

import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.data.Payload
import java.io.IOException
import java.io.PrintWriter

/**
 * A protocol that proxies requests to another [CommsProtocol] of a user's choosing
 *
 *
 * Created by tj.dahunsi on 2/11/17.
 */

class ProxyProtocol(printWriter: PrintWriter) : CommsProtocol(printWriter) {

    private var choosing: Boolean = false
    private var protocol: CommsProtocol? = null
    private val protocolMap = mutableMapOf(
            RC_REMOTE to BleRcProtocol(printWriter),
            KNOCK_KNOCK to KnockKnockProtocol(printWriter)
    )

    override fun processInput(payload: Payload): Payload {
        var output = Payload(payload.key)

        val action = payload.action

        when (action) {
            PING -> protocol?.let { return it.processInput(payload) }
                    ?: return closeAndChoose(output)
            RESET_CURRENT -> return protocol?.processInput(RESET)
                    ?: return closeAndChoose(output, true)
            RESET, CHOOSER -> return closeAndChoose(output)
        }

        if (!choosing) return protocol?.processInput(payload) ?: closeAndChoose(output)

        // Choose the protocol to proxy through
        protocol = when (action) {
            CONNECT_RC_REMOTE -> protocolMap.getOrPut(action) { ScanBleRcProtocol(printWriter) }
            RC_REMOTE -> protocolMap.getOrPut(action) { BleRcProtocol(printWriter) }
            KNOCK_KNOCK -> protocolMap.getOrPut(action) { KnockKnockProtocol(printWriter) }
            ZIGBEE_CONTROLLER -> protocolMap.getOrPut(action) { ZigBeeProtocol(printWriter) }
            else -> return output.apply {
                response = getString(R.string.proxyprotocol_invalid_command)
                if (App.isAndroidThings) addCommand(CONNECT_RC_REMOTE)

                addCommand(KNOCK_KNOCK)
                addCommand(RC_REMOTE)
                addCommand(RESET)
            }
        }

        protocol?.also { protocol ->
            choosing = false

            var result = "Chose Protocol: " + protocol.javaClass.simpleName
            result += "\n"

            val delegatedPayload = protocol.processInput(PING)

            output = output.copy(key = delegatedPayload.key)
            delegatedPayload.data?.let { output.data = it }
            delegatedPayload.action?.let { output.action = it }
            output.response = result + delegatedPayload.response

            for (command in delegatedPayload.commands) output.addCommand(command)

            output.addCommand(CHOOSER)
            output.addCommand(RESET_CURRENT)
            output.addCommand(RESET)
        }

        return output
    }

    private fun closeAndChoose(payload: Payload, resetProtocol: Boolean = false): Payload {
        if (resetProtocol) protocol?.processInput(RESET)
        choosing = true

        return payload.apply {
            response = appContext.getString(R.string.proxyprotocol_ping_response)
            addCommand(ZIGBEE_CONTROLLER)
            addCommand(KNOCK_KNOCK)
            addCommand(RC_REMOTE)
            addCommand(RESET_CURRENT)
            addCommand(RESET)

            if (App.isAndroidThings) payload.addCommand(CONNECT_RC_REMOTE)
        }
    }

    @Throws(IOException::class)
    override fun close() {
        protocolMap.values.forEach {
            try {
                it.close()
            } catch (exception: IOException) {
                exception.printStackTrace()
            }
        }
        protocol = null
    }

    companion object {

        private const val CHOOSER = "choose"
        private const val RESET_CURRENT = "Reset current"
        private const val KNOCK_KNOCK = "Knock Knock Jokes"
        private const val RC_REMOTE = "Control Remote Device"
        private const val CONNECT_RC_REMOTE = "Connect Remote Device"
        private const val ZIGBEE_CONTROLLER = "Control Zigbee Devices"

    }
}
