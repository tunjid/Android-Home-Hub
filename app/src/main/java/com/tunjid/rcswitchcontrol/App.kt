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

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.Service
import android.app.UiModeManager
import android.content.Context
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import com.google.android.things.pio.PeripheralManager
import com.tunjid.rcswitchcontrol.a433mhz.models.RfSwitch
import com.tunjid.rcswitchcontrol.common.Broadcaster
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.tunjid.rcswitchcontrol.services.ClientNsdService


/**
 * App Singleton.
 *
 *
 * Created by tj.dahunsi on 3/25/17.
 */

class App : android.app.Application() {

    private val receiver: WifiStatusReceiver by lazy {
        val receiver = WifiStatusReceiver()
        registerReceiver(receiver, IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)
        })

        Broadcaster.listen(ClientNsdService.ACTION_START_NSD_DISCOVERY)
                .subscribe({ intent -> receiver.onReceive(this, intent) }, Throwable::printStackTrace)

        receiver
    }

    @SuppressLint("CheckResult")
    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) = receiver.let { Unit } // Initialize lazy receiver

            override fun onActivityResumed(activity: Activity) = Unit

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    companion object {

        fun isServiceRunning(service: Class<out Service>): Boolean {
            val activityManager = ContextProvider.appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val services = activityManager.getRunningServices(Integer.MAX_VALUE)

            for (info in services) if (info.service.className == service.name) return true
            return false
        }

        val preferences: SharedPreferences
            get() = ContextProvider.appContext.getSharedPreferences(RfSwitch.SWITCH_PREFS, Context.MODE_PRIVATE)

        val isLandscape get() = isAndroidThings || isAndroidTV

        // Thrown on non Android things devices
        val isAndroidThings: Boolean
            get() {
                val uiModeManager = ContextProvider.appContext.getSystemService(UI_MODE_SERVICE) as UiModeManager
                if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
                    return true
                }

                return try {
                    PeripheralManager.getInstance().let { true }
                } catch (e: NoClassDefFoundError) {
                    false
                }
            }

        private val isAndroidTV: Boolean
            get() = ContextProvider.appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        fun catcher(tag: String, log: String, runnable: () -> Unit) {
            try {
                runnable.invoke()
            } catch (e: Exception) {
                Log.e(tag, log, e)
            }

        }
    }
}
