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

        // First connection, return here
        when (action) {
            PING -> {
                // Ping the existing protocol, otherwise fall through
                if (protocol != null) return protocol!!.processInput(payload)
                try {
                    if (protocol != null) protocol!!.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to close current CommsProtocol in ProxyProtocol", e)
                }

                choosing = true
                builder.setResponse(appContext.getString(R.string.proxyprotocol_ping_response))
                if (App.isAndroidThings) builder.addCommand(CONNECT_RC_REMOTE)
                builder.addCommand(KNOCK_KNOCK)
                builder.addCommand(RC_REMOTE)
                builder.addCommand(RESET)
                return builder.build()
            }
            RESET, CHOOSER -> {
                try {
                    if (protocol != null) protocol!!.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to close current CommsProtocol in ProxyProtocol", e)
                }

                choosing = true
                builder.setResponse(appContext.getString(R.string.proxyprotocol_ping_response))
                if (App.isAndroidThings) builder.addCommand(CONNECT_RC_REMOTE)
                builder.addCommand(KNOCK_KNOCK)
                builder.addCommand(RC_REMOTE)
                builder.addCommand(RESET)
                return builder.build()
            }
        }

        // Choose the protocol to proxy through
        if (choosing) {
            when (action) {
                CONNECT_RC_REMOTE -> protocol = ScanBleRcProtocol(printWriter)
                RC_REMOTE -> protocol = BleRcProtocol(printWriter)
                KNOCK_KNOCK -> protocol = KnockKnockProtocol(printWriter)
                else -> {
                    builder.setResponse("Invalid command. Please choose the server you want, Knock Knock jokes, or an RC Remote")
                    if (App.isAndroidThings) builder.addCommand(CONNECT_RC_REMOTE)
                    builder.addCommand(KNOCK_KNOCK)
                    builder.addCommand(RC_REMOTE)
                    builder.addCommand(RESET)
                    return builder.build()
                }
            }

            choosing = false

            var result = "Chose Protocol: " + protocol!!.javaClass.simpleName
            result += "\n"
            result += "\n"

            val toDeploy = protocol!!.processInput(PING)

            builder.setKey(toDeploy.key)
            builder.setData(toDeploy.data)
            builder.setResponse(result + toDeploy.response)

            for (command in toDeploy.commands) builder.addCommand(command)
            builder.addCommand(RESET)

            return builder.build()
        }

        return protocol!!.processInput(payload)
    }

    @Throws(IOException::class)
    override fun close() {
        if (protocol != null) protocol!!.close()
    }

    companion object {

        private val TAG = ProxyProtocol::class.java.simpleName

        private const val CHOOSER = "choose"
        private const val KNOCK_KNOCK = "Knock Knock Jokes"
        private const val RC_REMOTE = "Control Remote Device"
        private const val CONNECT_RC_REMOTE = "Connect Remote Device"
    }
}
