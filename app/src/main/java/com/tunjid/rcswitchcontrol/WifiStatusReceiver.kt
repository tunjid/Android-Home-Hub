/*
 * MIT License
 *
 * Copyright (c) 2019 Adetunji Dahunsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tunjid.rcswitchcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.WIFI_SERVICE
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.EXTRA_NETWORK_INFO
import android.net.wifi.WifiManager.EXTRA_SUPPLICANT_CONNECTED
import android.net.wifi.WifiManager.WIFI_STATE_ENABLED
import android.os.Handler
import android.os.Looper
import com.tunjid.androidx.communications.nsd.NsdHelper
import com.tunjid.rcswitchcontrol.common.Broadcaster
import com.tunjid.rcswitchcontrol.services.ClientNsdService
import com.tunjid.rcswitchcontrol.services.ServerNsdService
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

        // If we're designated as a NSD server, start it.
        if (ServerNsdService.isServer) context.startService(Intent(context, ServerNsdService::class.java))

        val lastServer = ClientNsdService.lastConnectedService ?: return
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
