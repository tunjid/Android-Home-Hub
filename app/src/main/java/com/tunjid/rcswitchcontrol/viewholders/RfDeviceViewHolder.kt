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

package com.tunjid.rcswitchcontrol.viewholders

import android.view.ViewGroup
import com.tunjid.androidx.recyclerview.viewbinding.BindingViewHolder
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderDelegate
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderFrom
import com.tunjid.rcswitchcontrol.control.Device
import com.tunjid.rcswitchcontrol.databinding.ViewholderRemoteSwitchBinding
import com.tunjid.rcswitchcontrol.utils.makeAccessibleForTV

interface RfDeviceListener : DeviceLongClickListener

var BindingViewHolder<ViewholderRemoteSwitchBinding>.device by viewHolderDelegate<Device.RF>()
var BindingViewHolder<ViewholderRemoteSwitchBinding>.listener by viewHolderDelegate<RfDeviceListener>()

fun BindingViewHolder<ViewholderRemoteSwitchBinding>.bind(device: Device.RF) {
    this.device = device
    binding.switchName.text = device.name
    highlight(device)
}

fun ViewGroup.rfDeviceDeviceViewHolder(
        listener: RfDeviceListener
) = viewHolderFrom(ViewholderRemoteSwitchBinding::inflate).apply binding@{
    this.listener = listener

    binding.apply {
        cardView.makeAccessibleForTV(stroked = true)
        itemView.setOnClickListener { listener.onClicked(device) }
        itemView.setOnLongClickListener {
            listener.onLongClicked(device)
            true
        }

        offSwitch.makeAccessibleForTV(stroked = true)
        onSwitch.makeAccessibleForTV(stroked = true)

        offSwitch.setOnClickListener { listener.onSwitchToggled(device, false) }
        onSwitch.setOnClickListener { listener.onSwitchToggled(device, true) }
    }
}
