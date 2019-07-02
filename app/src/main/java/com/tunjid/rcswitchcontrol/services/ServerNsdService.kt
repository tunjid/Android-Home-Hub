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
import com.tunjid.rcswitchcontrol.data.RcSwitch.Companion.SWITCH_PREFS
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.serialize
import com.tunjid.rcswitchcontrol.io.ConsoleWriter
import com.tunjid.rcswitchcontrol.nsd.protocols.CommsProtocol
import com.tunjid.rcswitchcontrol.nsd.protocols.ProxyProtocol
import io.reactivex.disposables.CompositeDisposable
import java.io.Closeable
import java.io.IOException
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

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
        override fun getService(): ServerNsdService = this@ServerNsdService
    }

    /**
     * Thread for communications between [ServerNsdService] and it's clients
     */
    private class ServerThread internal constructor(private val serverSocket: ServerSocket) : Thread(), Closeable {

        @Volatile
        internal var isRunning: Boolean = false
        private val portMap = ConcurrentHashMap<Int, Connection>()

        private val protocol = ProxyProtocol(ConsoleWriter(this::broadcastToClients))
        private val pool = Executors.newFixedThreadPool(5)

        override fun run() {
            isRunning = true

            Log.d(TAG, "ServerSocket Created, awaiting connections.")

            while (isRunning) {
                try {
                    Connection( // Create new connection for every new client
                            serverSocket.accept(), // Block this ServerThread till a socket connects
                            this::onClientWrite,
                            this::broadcastToClients,
                            this::onConnectionOpened,
                            this::onConnectionClosed)
                            .apply { pool.submit(this) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating client connection: ", e)
                }
            }

            Log.d(TAG, "ServerSocket Dead.")
        }

        private fun onConnectionOpened(port: Int, connection: Connection) {
            portMap[port] = connection
            Log.d(TAG, "Client connected. Number of clients: ${portMap.size}")
        }

        private fun onConnectionClosed(port: Int) {
            portMap.remove(port)
            Log.d(TAG, "Client left. Number of clients: ${portMap.size}")
        }

        private fun onClientWrite(input: String?): String {
            Log.d(TAG, "Read from client stream: $input")
            return protocol.processInput(input).serialize()
        }

        @Synchronized
        private fun broadcastToClients(output: String) {
            Log.d(TAG, "Writing to all connections: ${portMap.size}")
            pool.execute { portMap.values.forEach { it.outWriter.println(output) } }
        }

        override fun close() {
            isRunning = false

            for (key in portMap.keys) catcher(TAG, "Closing server connection with id $key") { portMap[key]?.close() }

            portMap.clear()
            catcher(TAG, "Closing server socket.") { serverSocket.close() }
            catcher(TAG, "Shutting down execution pool.") { pool.shutdown() }
        }
    }

    /**
     * Connection between [ServerNsdService] and it's clients
     */
    private class Connection internal constructor(
            private val socket: Socket,
            private val inputProcessor: (input: String?) -> String,
            private val outputProcessor: (output: String) -> Unit,
            private val onOpen: (port: Int, connection: Connection) -> Unit,
            private val onClose: (port: Int) -> Unit
    ) : Runnable, Closeable {

        val port: Int = socket.port
        lateinit var outWriter: PrintWriter

        override fun run() {
            if (!socket.isConnected) return

            onOpen.invoke(port, this)

            try {
                outWriter = createPrintWriter(socket)
                val reader = createBufferedReader(socket)

                // Initiate conversation with client
                outputProcessor.invoke(inputProcessor.invoke(CommsProtocol.PING))

                while (true) {
                    val input = reader.readLine() ?: break
                    val output = inputProcessor.invoke(input)
                    outputProcessor.invoke(output)

                    if (output == "Bye.") break
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                try {
                    close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        @Throws(IOException::class)
        override fun close() {
            onClose.invoke(port)
            socket.close()
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
