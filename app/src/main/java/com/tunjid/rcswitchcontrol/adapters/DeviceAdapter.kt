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

import android.view.ViewGroup
import com.tunjid.androidbootstrap.recyclerview.InteractiveAdapter
import com.tunjid.androidbootstrap.recyclerview.InteractiveViewHolder
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.data.Device
import com.tunjid.rcswitchcontrol.data.RfSwitch
import com.tunjid.rcswitchcontrol.data.ZigBeeDevice

typealias ViewHolder = InteractiveViewHolder<out InteractiveAdapter.AdapterListener>

class DeviceAdapter(
        switchListener: DeviceAdapterListener,
        private val switches: List<Device>
) : InteractiveAdapter<ViewHolder, DeviceAdapterListener>(switchListener) {

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder = when (viewType) {
        RF_DEVICE -> RfDeviceViewHolder(getItemView(R.layout.viewholder_remote_switch, viewGroup), adapterListener)
        ZIG_BEE_DEVICE -> ZigBeeDeviceViewHolder(getItemView(R.layout.viewholder_zigbee_device, viewGroup), adapterListener)
        else -> throw IllegalArgumentException("Invalid object type")
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = when (val device = switches[position]) {
        is RfSwitch -> (holder as RfDeviceViewHolder).bind(device)
        is ZigBeeDevice -> (holder as ZigBeeDeviceViewHolder).bind(device)
        else -> throw IllegalArgumentException("Invalid object type")
    }

    override fun getItemViewType(position: Int): Int = when (switches[position]) {
        is RfSwitch -> RF_DEVICE
        is ZigBeeDevice -> ZIG_BEE_DEVICE
        else -> throw IllegalArgumentException("Invalid object type")
    }

    override fun getItemCount(): Int = switches.size

    override fun getItemId(position: Int): Long = switches[position].hashCode().toLong()

    companion object {
        private const val RF_DEVICE = 1
        private const val ZIG_BEE_DEVICE = 2
    }
}

interface DeviceLongClickListener : InteractiveAdapter.AdapterListener {
    fun onClicked(device: Device)

    fun onLongClicked(device: Device): Boolean

    fun onSwitchToggled(device: Device, state: Boolean)

    fun isSelected(device: Device): Boolean
}

interface DeviceAdapterListener : InteractiveAdapter.AdapterListener,
        DeviceLongClickListener,
        RfDeviceViewHolder.Listener,
        ZigBeeDeviceViewHolder.Listener
