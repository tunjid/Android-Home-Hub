package com.tunjid.rcswitchcontrol

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import com.google.android.things.pio.PeripheralManager
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.tunjid.rcswitchcontrol.services.ClientNsdService

/**
 * App Singleton.
 *
 *
 * Created by tj.dahunsi on 3/25/17.
 */

class App : android.app.Application() {

    private var receiver: WifiStatusReceiver? = null

    @SuppressLint("CheckResult")
    override fun onCreate() {
        super.onCreate()
        instance = this

        Broadcaster.listen(ClientNsdService.ACTION_START_NSD_DISCOVERY)
                .subscribe({ intent -> if (receiver != null) receiver!!.onReceive(this, intent) },
                        Throwable::printStackTrace)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                // Necessary because of background restrictions, services may only be started in
                // the foreground
                if (receiver != null) return

                val filter = IntentFilter()
                filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                filter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)

                registerReceiver(WifiStatusReceiver(), filter)
            }

            override fun onActivityResumed(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {}

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    companion object {

        lateinit var instance: App

        fun isServiceRunning(service: Class<out Service>): Boolean {
            val activityManager = instance.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val services = activityManager.getRunningServices(Integer.MAX_VALUE)

            for (info in services) {
                if (info.service.className == service.name) return true
            }
            return false
        }

        // Thrown on non Android things devices
        val isAndroidThings: Boolean
            get() {
                try {
                    PeripheralManager.getInstance()
                } catch (e: NoClassDefFoundError) {
                    return false
                }

                return true
            }

        fun catcher(tag: String, log: String, runnable: () -> Unit) {
            try {
                runnable.invoke()
            } catch (e: Exception) {
                Log.e(tag, log, e)
            }

        }
    }
}
