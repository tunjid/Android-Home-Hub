package com.tunjid.rcswitchcontrol.nsd.protocols

import android.util.Log

import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.model.Payload

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

    override fun processInput(payload: Payload): Payload {
        val builder = Payload.builder()
        builder.setKey(javaClass.name)

        val action = payload.action

        when (action) {
            PING -> protocol?.let { return it.processInput(payload) }
                    ?: return closeAndChoose(builder)
            RESET, CHOOSER -> return closeAndChoose(builder)
        }

        if (!choosing) return protocol?.processInput(payload) ?: closeAndChoose(builder)

        // Choose the protocol to proxy through
        when (action) {
            CONNECT_RC_REMOTE -> protocol = ScanBleRcProtocol(printWriter)
            RC_REMOTE -> protocol = BleRcProtocol(printWriter)
            KNOCK_KNOCK -> protocol = KnockKnockProtocol(printWriter)
            else -> return builder.let {
                it.setResponse(getString(R.string.proxyprotocol_invalid_command))
                if (App.isAndroidThings) it.addCommand(CONNECT_RC_REMOTE)

                it.addCommand(KNOCK_KNOCK)
                        .addCommand(RC_REMOTE)
                        .addCommand(RESET)
                        .build()
            }
        }

        protocol?.let { protocol ->
            choosing = false

            var result = "Chose Protocol: " + protocol.javaClass.simpleName
            result += "\n"

            val delegatedPayload = protocol.processInput(PING)

            delegatedPayload.key?.let { builder.setKey(it) }
            delegatedPayload.data?.let { builder.setData(it) }
            delegatedPayload.action?.let { builder.setAction(it) }
            builder.setResponse(result + delegatedPayload.response)

            for (command in delegatedPayload.commands) builder.addCommand(command)
            builder.addCommand(RESET)
        }

        return builder.build()
    }

    private fun closeAndChoose(builder: Payload.Builder): Payload {
        try {
            close()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to close current CommsProtocol in ProxyProtocol", e)
        }

        choosing = true

        builder.setResponse(appContext.getString(R.string.proxyprotocol_ping_response))
                .addCommand(KNOCK_KNOCK)
                .addCommand(RC_REMOTE)
                .addCommand(RESET)

        if (App.isAndroidThings) builder.addCommand(CONNECT_RC_REMOTE)

        return builder.build()
    }

    @Throws(IOException::class)
    override fun close() = protocol?.close() ?: Unit

    companion object {

        private val TAG = ProxyProtocol::class.java.simpleName

        private const val CHOOSER = "choose"
        private const val KNOCK_KNOCK = "Knock Knock Jokes"
        private const val RC_REMOTE = "Control Remote Device"
        private const val CONNECT_RC_REMOTE = "Connect Remote Device"
    }
}
