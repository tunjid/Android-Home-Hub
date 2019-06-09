package com.tunjid.rcswitchcontrol.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdServiceInfo
import android.os.IBinder
import android.util.Log
import com.tunjid.androidbootstrap.communications.nsd.NsdHelper
import com.tunjid.androidbootstrap.communications.nsd.NsdHelper.createBufferedReader
import com.tunjid.androidbootstrap.communications.nsd.NsdHelper.createPrintWriter
import com.tunjid.androidbootstrap.core.components.ServiceConnection
import com.tunjid.rcswitchcontrol.App.Companion.catcher
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.tunjid.rcswitchcontrol.model.RcSwitch.Companion.SWITCH_PREFS
import com.tunjid.rcswitchcontrol.nsd.protocols.CommsProtocol
import com.tunjid.rcswitchcontrol.nsd.protocols.ProxyProtocol
import io.reactivex.disposables.CompositeDisposable
import java.io.Closeable
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Service hosting a [CommsProtocol] on network service discovery
 */
class ServerNsdService : Service() {

    private lateinit var nsdHelper: NsdHelper
    private lateinit var serverThread: ServerThread
    var serviceName: String? = null
        private set

    private val binder = Binder()

    private val disposable = CompositeDisposable()

    val isRunning: Boolean
        get() = serverThread.isRunning

    override fun onCreate() {
        super.onCreate()
        initialize()

        disposable.add(Broadcaster.listen(ACTION_STOP).subscribe({
            tearDown()
            stopSelf()
        }, Throwable::printStackTrace))
    }

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    private fun tearDown() {
        serverThread.close()
        nsdHelper.tearDown()
    }

    override fun onDestroy() {
        super.onDestroy()

        disposable.clear()
        tearDown()
    }

    fun restart() {
        tearDown()
        initialize()
    }

    private fun initialize() {
        val initialServiceName = getSharedPreferences(SWITCH_PREFS, Context.MODE_PRIVATE)
                .getString(SERVICE_NAME_KEY, WIRELESS_SWITCH_SERVICE)

        // Since discovery will happen via Nsd, we don't need to care which port is
        // used, just grab an avaialable one and advertise it via Nsd.

        try {
            val serverSocket = ServerSocket(0)
            nsdHelper = NsdHelper.getBuilder(this)
                    .setRegisterSuccessConsumer(this::onNsdServiceRegistered)
                    .setRegisterErrorConsumer { service, error -> Log.i(TAG, "Could not register service " + service.serviceName + ". Error code: " + error) }
                    .build().apply { registerService(serverSocket.localPort, initialServiceName) }

            serverThread = ServerThread(serverSocket).apply { start() }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }

    }

    private fun onNsdServiceRegistered(service: NsdServiceInfo) {
        serviceName = service.serviceName
        getSharedPreferences(SWITCH_PREFS, Context.MODE_PRIVATE).edit()
                .putString(SERVICE_NAME_KEY, serviceName)
                .putBoolean(SERVER_FLAG, true)
                .apply()

        Log.i(TAG, "Registered data for: " + serviceName!!)
    }

    /**
     * [android.os.Binder] for [ServerNsdService]
     */
    private inner class Binder : ServiceConnection.Binder<ServerNsdService>() {
        override fun getService(): ServerNsdService {
            return this@ServerNsdService
        }
    }

    /**
     * Thread for communications between [ServerNsdService] and it's clients
     */
    private class ServerThread internal constructor(private val serverSocket: ServerSocket?) : Thread(), Closeable {

        @Volatile
        internal var isRunning: Boolean = false
        private val connectionsMap = ConcurrentHashMap<Long, Connection>()

        override fun run() {
            isRunning = true

            Log.d(TAG, "ServerSocket Created, awaiting connection.")

            while (isRunning) {
                try {
                    // Create new connection for every new client
                    val connection = Connection(serverSocket!!.accept(), connectionsMap).apply { start() }
                    connectionsMap[connection.id] = connection

                    Log.d(TAG, "Client connected. Number of clients: " + connectionsMap.size)
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating client connection: ", e)
                }

            }
            Log.d(TAG, "ServerSocket Dead.")
        }

        override fun close() {
            isRunning = false

            for (key in connectionsMap.keys) catcher(TAG, "Closing server connection with id $key") { connectionsMap[key]?.close() }

            connectionsMap.clear()
            if (serverSocket != null) catcher(TAG, "Closing server socket.") { serverSocket.close() }
        }
    }

    /**
     * Connection between [ServerNsdService] and it's clients
     */
    private class Connection internal constructor(
            private val socket: Socket?,
            private val connectionMap: MutableMap<Long, Connection>
    ) : Thread(), Closeable {

        init {
            Log.d(TAG, "Connected to new client")
        }

        override fun run() {
            if (socket == null || !socket.isConnected) return

            var protocol: CommsProtocol? = null

            try {
                val `in` = createBufferedReader(socket)
                val out = createPrintWriter(socket)
                protocol = ProxyProtocol(out)

                var outputLine: String

                // Initiate conversation with client
                outputLine = protocol.processInput(null).serialize()

                out.println(outputLine)

                while (true) {
                    val inputLine = `in`.readLine() ?: break
                    outputLine = protocol.processInput(inputLine).serialize()
                    out.println(outputLine)

                    Log.d(TAG, "Read from client stream: $inputLine")

                    if (outputLine == "Bye.") break
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                try {
                    if (protocol != null)
                        try {
                            protocol.close()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }

                    close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
        }

        @Throws(IOException::class)
        override fun close() {
            connectionMap.remove(id)
            socket!!.close()
        }
    }

    companion object {

        private val TAG = ServerNsdService::class.java.simpleName

        const val SERVER_FLAG = "com.tunjid.rcswitchcontrol.ServerNsdService.services.server.flag"
        const val ACTION_STOP = "com.tunjid.rcswitchcontrol.ServerNsdService.services.server.stop"
        const val SERVICE_NAME_KEY = "com.tunjid.rcswitchcontrol.ServerNsdService.services.server.serviceName"
        const val WIRELESS_SWITCH_SERVICE = "Wireless Switch Service"
    }

}
