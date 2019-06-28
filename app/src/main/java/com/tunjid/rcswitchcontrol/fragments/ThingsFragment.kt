package com.tunjid.rcswitchcontrol.fragments

import android.os.Bundle

import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel
import androidx.lifecycle.ViewModelProviders

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.tunjid.rcswitchcontrol.data.RcSwitch
import com.tunjid.rcswitchcontrol.services.ClientBleService
import com.tunjid.rcswitchcontrol.services.ServerNsdService

class ThingsFragment : ClientBleFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireContext().getSharedPreferences(RcSwitch.SWITCH_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(ServerNsdService.SERVER_FLAG, true)
                .apply()

        ViewModelProviders.of(this).get(NsdClientViewModel::class.java)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // Request permission for location to enable ble scanning
        requestPermissions(arrayOf(ACCESS_COARSE_LOCATION), 0)
    }

    companion object {

        fun newInstance(device: BluetoothDevice? = null): ThingsFragment {
            val startFragment = ThingsFragment()
            val args = Bundle()
            if (device != null) args.putParcelable(ClientBleService.BLUETOOTH_DEVICE, device)

            startFragment.arguments = args
            return startFragment
        }
    }
}
