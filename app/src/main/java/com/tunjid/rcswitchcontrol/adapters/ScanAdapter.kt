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

package com.tunjid.rcswitchcontrol.adapters

import android.bluetooth.BluetoothDevice
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.tunjid.androidbootstrap.communications.bluetooth.ScanResultCompat
import com.tunjid.androidbootstrap.recyclerview.InteractiveAdapter
import com.tunjid.androidbootstrap.recyclerview.InteractiveViewHolder
import com.tunjid.rcswitchcontrol.R

/**
 * Adapter for BLE devices found while sacnning
 */
class ScanAdapter(
        adapterListener: AdapterListener,
        private val scanResults: List<ScanResultCompat>
) : InteractiveAdapter<ScanAdapter.ViewHolder, ScanAdapter.AdapterListener>(adapterListener) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(getItemView(R.layout.viewholder_scan, viewGroup), adapterListener)

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) =
            viewHolder.bind(scanResults[position])

    override fun getItemViewType(position: Int): Int = BLE_DEVICE

    override fun getItemCount(): Int = scanResults.size

    // ViewHolder for actual content
    class ViewHolder internal constructor(
            itemView: View,
            adapterListener: AdapterListener
    ) : InteractiveViewHolder<AdapterListener>(itemView, adapterListener) {

        private var deviceName: TextView = itemView.findViewById(R.id.device_name)
        private var deviceAddress: TextView = itemView.findViewById(R.id.device_address)

        private lateinit var result: ScanResultCompat

        init {
            itemView.setOnClickListener { adapterListener.onBluetoothDeviceClicked(result.device) }
        }

        internal fun bind(result: ScanResultCompat) {
            this.result = result

            deviceAddress.text = if (result.scanRecord != null) result.scanRecord!!.deviceName else ""
            deviceName.text = if (result.device != null) result.device.address else ""
        }

    }

    interface AdapterListener : InteractiveAdapter.AdapterListener {
        fun onBluetoothDeviceClicked(bluetoothDevice: BluetoothDevice)
    }

    companion object {

        private const val BLE_DEVICE = 1
    }
}
