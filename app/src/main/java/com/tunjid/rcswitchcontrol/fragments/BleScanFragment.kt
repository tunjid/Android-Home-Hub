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

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.M
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import com.tunjid.androidbootstrap.recyclerview.ListManager
import com.tunjid.androidbootstrap.recyclerview.ListManagerBuilder
import com.tunjid.androidbootstrap.recyclerview.ListPlaceholder
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.adapters.ScanAdapter
import com.tunjid.rcswitchcontrol.services.ClientBleService
import com.tunjid.rcswitchcontrol.viewmodels.BleScanViewModel

class BleScanFragment : BaseFragment(), ScanAdapter.AdapterListener {

    private var isScanning: Boolean = false

    private lateinit var listManager: ListManager<ScanAdapter.ViewHolder, ListPlaceholder<*>>
    private lateinit var viewModel: BleScanViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        viewModel = ViewModelProviders.of(this).get(BleScanViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_ble_scan, container, false)

        listManager = ListManagerBuilder<ScanAdapter.ViewHolder, ListPlaceholder<*>>()
                .withRecyclerView(root.findViewById(R.id.list))
                .addDecoration(DividerItemDecoration(requireActivity(), DividerItemDecoration.VERTICAL))
                .withAdapter(ScanAdapter(this, viewModel.scanResults))
                .withLinearLayoutManager()
                .build()

        return root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        toolBar.setTitle(R.string.button_scan)
        if (viewModel.hasBle()) return

        val activity = requireActivity()
        Toast.makeText(activity, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
        activity.onBackPressed()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_ble_scan, menu)

        menu.findItem(R.id.menu_stop).isVisible = isScanning
        menu.findItem(R.id.menu_scan).isVisible = !isScanning

        val refresh = menu.findItem(R.id.menu_refresh)

        refresh.isVisible = isScanning
        if (isScanning) refresh.setActionView(R.layout.actionbar_indeterminate_progress)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_scan -> scanDevices(true)
            R.id.menu_stop -> scanDevices(false)
        }
        return true
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopScanning()
    }

    override fun onResume() {
        super.onResume()

        // Ensures BT is enabled on the device.  If BT is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!viewModel.isBleOn) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        val noPermit = SDK_INT >= M && ActivityCompat.checkSelfPermission(requireActivity(),
                ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED

        if (noPermit)
            requestPermissions(arrayOf(ACCESS_COARSE_LOCATION), REQUEST_ENABLE_BT)
        else
            scanDevices(true)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_ENABLE_BT -> {
                // If request is cancelled, the result arrays are empty.
                val canScan = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                if (canScan) scanDevices(true)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            requireActivity().onBackPressed()
        }
    }

    override fun onDestroyView() {
        listManager.clear()
        super.onDestroyView()
    }

    override fun onBluetoothDeviceClicked(bluetoothDevice: BluetoothDevice) {
        if (isScanning) scanDevices(false)

        val activity = requireActivity()
        activity.startService(Intent(activity, ClientBleService::class.java)
                .putExtra(ClientBleService.BLUETOOTH_DEVICE, bluetoothDevice))

        showFragment(ClientBleFragment.newInstance(bluetoothDevice))
    }

    private fun scanDevices(enable: Boolean) {
        isScanning = enable

        if (isScanning) disposables.add(viewModel.findDevices()
                .doOnSubscribe { requireActivity().invalidateOptionsMenu() }
                .doFinally(this::onScanningStopped)
                .subscribe(listManager::onDiff, Throwable::printStackTrace))
        else viewModel.stopScanning()
    }

    private fun onScanningStopped() {
        isScanning = false
        requireActivity().invalidateOptionsMenu()
    }

    companion object {

        private const val REQUEST_ENABLE_BT = 1

        fun newInstance(): BleScanFragment {
            val fragment = BleScanFragment()
            val args = Bundle()
            fragment.arguments = args
            return fragment
        }
    }
}