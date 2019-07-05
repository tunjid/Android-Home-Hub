package com.tunjid.rcswitchcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class USBDeviceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(this::class.java.simpleName, "USB Device Receiver Called")
    }
}
