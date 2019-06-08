package com.tunjid.rcswitchcontrol.interfaces

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build

import com.tunjid.rcswitchcontrol.App

import androidx.annotation.StringRes

import android.content.Context.NOTIFICATION_SERVICE

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

        val app = App.instance
        val channel = NotificationChannel(NOTIFICATION_TYPE, app.getString(name), NotificationManager.IMPORTANCE_LOW)
        channel.description = app.getString(description)

        val manager = app.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {

        const val NOTIFICATION_TYPE = "RC_SWITCH_SERVICE"
    }
}
