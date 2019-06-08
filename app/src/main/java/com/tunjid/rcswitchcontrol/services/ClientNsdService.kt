package com.tunjid.rcswitchcontrol.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.annotation.StringDef
import androidx.core.app.NotificationCompat
import com.tunjid.androidbootstrap.communications.nsd.NsdHelper
import com.tunjid.androidbootstrap.communications.nsd.NsdHelper.createBufferedReader
import com.tunjid.androidbootstrap.communications.nsd.NsdHelper.createPrintWriter
import com.tunjid.androidbootstrap.core.components.ServiceConnection
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.activities.MainActivity
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.tunjid.rcswitchcontrol.interfaces.ClientStartedBoundService
import com.tunjid.rcswitchcontrol.model.Payload
import com.tunjid.rcswitchcontrol.model.RcSwitch.Companion.SWITCH_PREFS
import io.reactivex.disposables.CompositeDisposable
import java.io.Closeable
import java.io.IOException
import java.io.PrintWriter
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy.SOURCE
import java.net.Socket
import java.util.*


class ClientNsdService : Service(), ClientStartedBoundService {

    private var isUserInApp: Boolean = false
    private var nsdHelper: NsdHelper? = null
    private var currentService: NsdServiceInfo? = null

    @ConnectionState
    var connectionState = ACTION_SOCKET_DISCONNECTED
        set(value) {
            field = value
            Broadcaster.push(Intent(connectionState))

            if (!isConnected) return  // Update the notification
            if (!isUserInApp) startForeground(NOTIFICATION_ID, connectedNotification())
            else stopForeground(true)

            getSharedPreferences(SWITCH_PREFS, Context.MODE_PRIVATE).edit()
                    .putString(LAST_CONNECTED_SERVICE, currentService!!.serviceName).apply()
        }

    private var messageThread: MessageThread? = null
    private val messageQueue = LinkedList<String>()

    private val disposable = CompositeDisposable()
    private val binder = NsdClientBinder()

    override val isConnected: Boolean
        get() = connectionState == ACTION_SOCKET_CONNECTED

    val serviceName: String?
        get() = if (currentService == null) null else currentService!!.serviceName

    @Retention(SOURCE)
    @StringDef(ACTION_SOCKET_CONNECTED, ACTION_SOCKET_CONNECTING, ACTION_SOCKET_DISCONNECTED)
    internal annotation class ConnectionState

    override fun onCreate() {
        super.onCreate()
        addChannel(R.string.switch_service, R.string.switch_service_description)

        nsdHelper = NsdHelper.getBuilder(this).build()
        disposable.add(Broadcaster.listen(ACTION_STOP).subscribe({ intent ->
            stopForeground(true)
            tearDown()
            stopSelf()
        }, Throwable::printStackTrace))
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        initialize(intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        onAppForeGround()
        initialize(intent)
        return binder
    }

    override fun initialize(intent: Intent?) {
        if (isConnected || intent == null || !intent.hasExtra(NSD_SERVICE_INFO_KEY)) return

        currentService = intent.getParcelableExtra(NSD_SERVICE_INFO_KEY)
        currentService?.run { connect(this) }
    }

    override fun onAppBackground() {
        isUserInApp = false

        // Use a notification to tell the user the app is running
        if (isConnected)
            startForeground(NOTIFICATION_ID, connectedNotification())
        else
            stopForeground(true)// Otherwise, remove the notification and wait for a reconnect
    }

    override fun onAppForeGround() {
        isUserInApp = true
        stopForeground(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        tearDown()
        disposable.clear()
    }

    private fun connect(serviceInfo: NsdServiceInfo) {

        // If we're already connected to this service, return
        if (isConnected) return

        connectionState = ACTION_SOCKET_CONNECTING

        // Initialize current service if we are starting up the first time
        if (messageThread == null) {
            messageThread = MessageThread(serviceInfo, this)
        } else if (serviceInfo != messageThread!!.service) {
            tearDown()
            messageThread = MessageThread(serviceInfo, this)
        }// We're binding to an entirely new service. Tear down the current state

        messageThread!!.start()
    }

    fun sendMessage(payload: Payload) {
        messageQueue.add(payload.serialize())
        if (messageThread != null) messageThread!!.send(messageQueue.remove())
    }

    protected fun tearDown() {
        nsdHelper!!.tearDown()

        Log.e(TAG, "Tearing down ClientServer")
        if (messageThread != null) messageThread!!.close()
    }

    private fun connectedNotification(): Notification {

        val resumeIntent = Intent(this, MainActivity::class.java)

        resumeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        resumeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        resumeIntent.putExtra(NSD_SERVICE_INFO_KEY, currentService)

        val activityPendingIntent = PendingIntent.getActivity(
                this, 0, resumeIntent, PendingIntent.FLAG_CANCEL_CURRENT)

        val notificationBuilder = NotificationCompat.Builder(this, ClientStartedBoundService.NOTIFICATION_TYPE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getText(R.string.connected))
                .setContentText(getText(R.string.connected_to_server))
                .setContentIntent(activityPendingIntent)

        return notificationBuilder.build()
    }

    private inner class NsdClientBinder : ServiceConnection.Binder<ClientNsdService>() {
        // Binding impl
        override fun getService(): ClientNsdService {
            return this@ClientNsdService
        }
    }

    private class MessageThread internal constructor(
            internal var service: NsdServiceInfo,
            internal var clientNsdService: ClientNsdService) : Thread(), Closeable {

        private lateinit var currentSocket: Socket
        private var out: PrintWriter? = null

        override fun run() {
            try {
                Log.d(TAG, "Initializing client-side socket. Host: " + service.host + ", Port: " + service.port)

                currentSocket = Socket(service.host, service.port)

                out = createPrintWriter(currentSocket)
                val `in` = createBufferedReader(currentSocket)

                Log.d(TAG, "Connection-side socket initialized.")

                clientNsdService.connectionState = ACTION_SOCKET_CONNECTED

                if (!clientNsdService.messageQueue.isEmpty()) {
                    out!!.println(clientNsdService.messageQueue.remove())
                }

                while (true) {
                    val fromServer = `in`.readLine() ?: break

                    Log.i(TAG, "Server: $fromServer")

                    val serverResponse = Intent()
                    serverResponse.action = ACTION_SERVER_RESPONSE
                    serverResponse.putExtra(DATA_SERVER_RESPONSE, fromServer)

                    Broadcaster.push(serverResponse)

                    if (fromServer == "Bye.") {
                        clientNsdService.connectionState = ACTION_SOCKET_DISCONNECTED
                        break
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                clientNsdService.connectionState = ACTION_SOCKET_DISCONNECTED
            } finally {
                close()
            }
        }

        internal fun send(message: String) {
            if (out == null) return

            if (out!!.checkError()) {
                close()
                Log.d(TAG, "Error writing to server, closing.")
            } else
                Thread {
                    out!!.println(message)
                    Log.d(TAG, "Connection sent message: $message")
                }.start()
        }

        override fun close() {
            App.catcher(TAG, "Exiting message thread") { currentSocket.close() }
            clientNsdService.connectionState = ACTION_SOCKET_DISCONNECTED
        }
    }

    companion object {

        const val NOTIFICATION_ID = 2
        private val TAG = ClientNsdService::class.java.simpleName

        const val LAST_CONNECTED_SERVICE = "com.tunjid.rcswitchcontrol.services.ClientNsdService.last connected service"
        const val NSD_SERVICE_INFO_KEY = "current Service key"

        const val ACTION_STOP = "com.tunjid.rcswitchcontrol.services.ClientNsdService.stop"
        const val ACTION_SERVER_RESPONSE = "com.tunjid.rcswitchcontrol.services.ClientNsdService.service.response"
        const val ACTION_SOCKET_CONNECTED = "com.tunjid.rcswitchcontrol.services.ClientNsdService.service.socket.connected"
        const val ACTION_SOCKET_CONNECTING = "com.tunjid.rcswitchcontrol.services.ClientNsdService.service.socket.connecting"
        const val ACTION_SOCKET_DISCONNECTED = "com.tunjid.rcswitchcontrol.services.ClientNsdService.service.socket.disconnected"
        const val ACTION_START_NSD_DISCOVERY = "com.tunjid.rcswitchcontrol.services.ClientNsdService.start.nsd.discovery"

        val DATA_SERVER_RESPONSE = "service_response"
    }
}
