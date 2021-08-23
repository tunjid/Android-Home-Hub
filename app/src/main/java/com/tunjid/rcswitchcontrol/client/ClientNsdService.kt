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

package com.tunjid.rcswitchcontrol.client

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.nsd.NsdServiceInfo
import com.rcswitchcontrol.protocols.models.Payload
import com.tunjid.androidx.core.components.services.SelfBinder
import com.tunjid.androidx.core.components.services.SelfBindingService
import com.tunjid.androidx.core.delegates.intentExtras
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.MainActivity
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.common.asSuspend
import com.tunjid.rcswitchcontrol.common.mapDistinct
import com.tunjid.rcswitchcontrol.di.dagger
import com.tunjid.rcswitchcontrol.di.stateMachine
import com.tunjid.rcswitchcontrol.utils.addNotificationChannel
import com.tunjid.rcswitchcontrol.utils.notificationBuilder
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.*

var Intent.nsdServiceInfo by intentExtras<NsdServiceInfo?>()
var Intent.nsdServiceName by intentExtras<String?>()

class ClientNsdService : Service(), SelfBindingService<ClientNsdService> {

    val state: StateFlow<State> by lazy { stateMachine.state }

    private val scope = dagger.appComponent.uiScope()
    private val stateMachine by stateMachine<ClientViewModel>()

    private val binder = NsdClientBinder()

    override fun onCreate() {
        super.onCreate()
        addNotificationChannel(R.string.switch_service, R.string.switch_service_description)

        val state = stateMachine.state
        scope.launch {
            state.collect(::onStateChanged)
        }
        scope.launch {
            state.mapDistinct(State::serviceName.asSuspend).collect {
                it?.let(Companion::lastConnectedService::set)
            }
        }
        scope.launch {
            state.mapDistinct(State::isStopped.asSuspend).collect {
                if (it) stopSelf()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stateMachine.close()
        scope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        initialize(intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): SelfBinder<ClientNsdService> {
        onAppForeGround()
        initialize(intent)
        return binder
    }

    private fun onStateChanged(state: State) {
        val connected = state.status is Status.Connected

        if (state.inBackground && connected && state.serviceName != null) startForeground(
            NOTIFICATION_ID,
            connectedNotification(state.serviceName)
        ) else stopForeground(true)
    }

    private fun initialize(intent: Intent?) {
        intent
            ?.nsdServiceInfo
            ?.let(Input::Connect)
            ?.let(stateMachine.accept)
    }

    fun onAppBackground() = stateMachine.accept(Input.ContextChanged(inBackground = true))

    fun onAppForeGround() = stateMachine.accept(Input.ContextChanged(inBackground = false))

    fun sendMessage(payload: Payload) = stateMachine.accept(Input.Send(payload))

    private fun connectedNotification(serviceName: String): Notification =
        notificationBuilder()
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getText(R.string.connected))
            .setContentText(getText(R.string.connected_to_server))
            .setContentIntent(PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    nsdServiceName = serviceName
                },
                PendingIntent.FLAG_CANCEL_CURRENT
            ))
            .build()

    private inner class NsdClientBinder : SelfBinder<ClientNsdService>() {
        override val service: ClientNsdService
            get() = this@ClientNsdService
    }

    companion object {
        const val NOTIFICATION_ID = 2

        private const val LAST_CONNECTED_SERVICE = "com.tunjid.rcswitchcontrol.client.ClientNsdService.last connected service"

        var lastConnectedService: String?
            get() = App.preferences.getString(LAST_CONNECTED_SERVICE, null)
            set(value) = when (value) {
                null -> App.preferences.edit().remove(LAST_CONNECTED_SERVICE).apply()
                else -> App.preferences.edit().putString(LAST_CONNECTED_SERVICE, value).apply()
            }
    }
}
