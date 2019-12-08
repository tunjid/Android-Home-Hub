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

package com.tunjid.rcswitchcontrol.interfaces

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import com.rcswitchcontrol.protocols.ContextProvider

/**
 * An interface hosting callbacks for [services][android.app.Service] that are started,
 * then bound to a UI element. Upon leaving the UI, a notification is displayed to the user.
 *
 *
 * Created by tj.dahunsi on 3/26/17.
 */

interface ClientStartedBoundService {

    val isConnected: Boolean

    fun initialize(intent: Intent?)

    fun onAppBackground()

    fun onAppForeGround()

    @TargetApi(Build.VERSION_CODES.O)
    fun addChannel(@StringRes name: Int, @StringRes description: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val app = ContextProvider.appContext
        val channel = NotificationChannel(NOTIFICATION_TYPE, app.getString(name), NotificationManager.IMPORTANCE_LOW)
        channel.description = app.getString(description)

        val manager = app.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {

        const val NOTIFICATION_TYPE = "RC_SWITCH_SERVICE"
    }
}
