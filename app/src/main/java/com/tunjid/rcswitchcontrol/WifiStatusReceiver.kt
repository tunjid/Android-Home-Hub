package com.tunjid.rcswitchcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Context.WIFI_SERVICE
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.EXTRA_NETWORK_INFO
import android.net.wifi.WifiManager.EXTRA_SUPPLICANT_CONNECTED
import android.net.wifi.WifiManager.WIFI_STATE_ENABLED
import android.os.Handler
import android.os.Looper
import com.tunjid.androidbootstrap.communications.nsd.NsdHelper
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.tunjid.rcswitchcontrol.model.RcSwitch.Companion.SWITCH_PREFS
import com.tunjid.rcswitchcontrol.services.ClientBleService
import com.tunjid.rcswitchcontrol.services.ClientNsdService
import com.tunjid.rcswitchcontrol.services.ClientNsdService.Companion.LAST_CONNECTED_SERVICE
import com.tunjid.rcswitchcontrol.services.ServerNsdService
import com.tunjid.rcswitchcontrol.services.ServerNsdService.Companion.SERVER_FLAG
import java.util.concurrent.atomic.AtomicReference

class WifiStatusReceiver : BroadcastReceiver() {

    private var isSearching: Boolean = false

    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val shouldConnect = when (intent.action ?: return) {
            ClientNsdService.ACTION_START_NSD_DISCOVERY -> (context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).isWifiEnabled
            WifiManager.WIFI_STATE_CHANGED_ACTION -> intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0) == WIFI_STATE_ENABLED
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> intent.getParcelableExtra<NetworkInfo>(EXTRA_NETWORK_INFO).isConnected
            WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION -> intent.getBooleanExtra(EXTRA_SUPPLICANT_CONNECTED, false)
            else -> false
        }

        if (shouldConnect) connectLastNsdService(context)
        else stopClient()
    }

    private fun connectLastNsdService(context: Context) {
        if (isSearching) return

        val preferences = context.getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE)

        // If we're running the BLE service, and we were designated as a NSD server, start it.
        if (App.isServiceRunning(ClientBleService::class.java) && preferences.getBoolean(SERVER_FLAG, false)) {
            context.startService(Intent(context, ServerNsdService::class.java))
            return
        }

        val lastServer = preferences.getString(LAST_CONNECTED_SERVICE, "") ?: return
        val helperReference = AtomicReference<NsdHelper>()

        val nsdHelper = NsdHelper.getBuilder(context)
                .setServiceFoundConsumer { service ->
                    val same = helperReference.get()
                    if (same != null && lastServer == service.serviceName)
                        same.resolveService(service)
                }
                .setResolveSuccessConsumer { service ->
                    if (lastServer != service.serviceName) return@setResolveSuccessConsumer

                    context.startService(Intent(context, ClientNsdService::class.java)
                            .putExtra(ClientNsdService.NSD_SERVICE_INFO_KEY, service))
                    helperReference.get().tearDown()
                    isSearching = false
                }
                .build()

        helperReference.set(nsdHelper)
        nsdHelper.discoverServices()
        isSearching = true

        Handler(Looper.myLooper()).postDelayed({
            if (helperReference.get() == null) return@postDelayed

            helperReference.get().tearDown()
            isSearching = false
        }, SEARCH_LENGTH_MILLIS.toLong())
    }

    private fun stopClient() {
        if (App.isServiceRunning(ClientNsdService::class.java)) {
            Broadcaster.push(Intent(ClientNsdService.ACTION_STOP))
        }
        //        if (App.isServiceRunning(ServerNsdService.class)) {
        //            broadcastManager.sendBroadcast(new Intent(ServerNsdService.ACTION_STOP));
        //        }
    }

    companion object {

        const val SEARCH_LENGTH_MILLIS = 15000
    }
}
