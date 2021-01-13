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
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.EXTRA_NETWORK_INFO
import android.net.wifi.WifiManager.EXTRA_SUPPLICANT_CONNECTED
import android.net.wifi.WifiManager.WIFI_STATE_ENABLED
import com.tunjid.rcswitchcontrol.di.dagger
import com.tunjid.rcswitchcontrol.models.Broadcast

class WifiStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val shouldConnect = when (intent.action ?: return) {
            WifiManager.WIFI_STATE_CHANGED_ACTION -> intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0) == WIFI_STATE_ENABLED
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> intent.getParcelableExtra<NetworkInfo>(EXTRA_NETWORK_INFO)?.isConnected == true
            WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION -> intent.getBooleanExtra(EXTRA_SUPPLICANT_CONNECTED, false)
            else -> false
        }

        context.dagger.appComponent.broadcaster(
            if (shouldConnect) Broadcast.ClientNsd.StartDiscovery()
            else Broadcast.ClientNsd.Stop
        )
    }
}
