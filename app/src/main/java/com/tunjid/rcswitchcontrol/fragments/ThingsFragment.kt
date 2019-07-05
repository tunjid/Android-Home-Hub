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

package com.tunjid.rcswitchcontrol.fragments

import android.os.Bundle

import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel
import androidx.lifecycle.ViewModelProviders

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.tunjid.rcswitchcontrol.data.RfSwitch
import com.tunjid.rcswitchcontrol.services.ClientBleService
import com.tunjid.rcswitchcontrol.services.ServerNsdService

class ThingsFragment : ClientBleFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireContext().getSharedPreferences(RfSwitch.SWITCH_PREFS, Context.MODE_PRIVATE)
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
