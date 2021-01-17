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

package com.tunjid.rcswitchcontrol.server

import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.rcswitchcontrol.protocols.CommsProtocol
import com.tunjid.androidx.core.components.services.SelfBinder
import com.tunjid.androidx.core.components.services.SelfBindingService
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.common.mapDistinct
import com.tunjid.rcswitchcontrol.di.viewModelFactory
import com.tunjid.rcswitchcontrol.interfaces.ClientStartedBoundService

/**
 * Service hosting a [CommsProtocol] on network service discovery
 */
class ServerNsdService : LifecycleService(), SelfBindingService<ServerNsdService> {

    private val binder = Binder()
    private val viewModel by viewModelFactory<ServerViewModel>()

    override fun onCreate() {
        super.onCreate()
        val service = this
        viewModel.state.apply {
            mapDistinct(State::status).observe(service, ::onStatusChanged)
            mapDistinct(State::numClients).observe(service) {
                Log.i("TEST", "There are $it clients")
            }
        }
    }

    private fun onStatusChanged(status: Status) = when (status) {
        Status.Initialized -> Unit
        Status.Registered -> startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, ClientStartedBoundService.NOTIFICATION_TYPE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getText(R.string.started_server_service))
                .build()
        )
        Status.Error -> restart()
        Status.Stopped -> stopSelf()
    }

    override fun onBind(intent: Intent): SelfBinder<ServerNsdService> {
        super.onBind(intent)
        return binder
    }

    fun restart() = viewModel.accept(Input.Restart)

    /**
     * [android.os.Binder] for [ServerNsdService]
     */
    private inner class Binder : SelfBinder<ServerNsdService>() {
        override val service: ServerNsdService
            get() = this@ServerNsdService
    }

    companion object {
        private const val SERVER_FLAG = "com.tunjid.rcswitchcontrol.ServerNsdService.services.server.flag"
        private const val SERVICE_NAME_KEY = "com.tunjid.rcswitchcontrol.ServerNsdService.services.server.serviceName"
        private const val WIRELESS_SWITCH_SERVICE = "Wireless Switch Service"
        private const val NOTIFICATION_ID = 3

        var serviceName: String
            get() = App.preferences.getString(SERVICE_NAME_KEY, WIRELESS_SWITCH_SERVICE)
                ?: WIRELESS_SWITCH_SERVICE
            set(value) = App.preferences.edit().putString(SERVICE_NAME_KEY, value).apply()

        var isServer: Boolean
            get() = App.preferences.getBoolean(SERVER_FLAG, App.isAndroidThings)
            set(value) = App.preferences.edit().putBoolean(SERVER_FLAG, value).apply()
    }
}
