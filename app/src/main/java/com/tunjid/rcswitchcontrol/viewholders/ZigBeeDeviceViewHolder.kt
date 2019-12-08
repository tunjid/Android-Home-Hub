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

import android.view.View
import android.widget.SeekBar
import com.tunjid.rcswitchcontrol.R
import com.rcswitchcontrol.zigbee.models.ZigBeeDevice
import com.tunjid.rcswitchcontrol.dialogfragments.Throttle

// ViewHolder for actual content
class ZigBeeDeviceViewHolder internal constructor(
        itemView: View,
        listener: Listener
) : DeviceViewHolder<ZigBeeDeviceViewHolder.Listener, ZigBeeDevice>(itemView, listener) {

    private val colorPicker = itemView.findViewById<View>(R.id.color_picker)
    private val zigBeeIcon = itemView.findViewById<View>(R.id.zigbee_icon)
    private val leveler = itemView.findViewById<SeekBar>(R.id.leveler)

    override fun bind(device: ZigBeeDevice) {
        super.bind(device)

        deviceName.text = device.name

        zigBeeIcon.setOnClickListener { listener.rediscover(device) }
        colorPicker.setOnClickListener { listener.color(device) }
        offSwitch.setOnClickListener { listener.onSwitchToggled(device, false) }
        onSwitch.setOnClickListener { listener.onSwitchToggled(device, true) }
        leveler.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            val throttle = Throttle { listener.level(device, it / 100F) }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = throttle.run(progress)

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    interface Listener : DeviceLongClickListener {
        fun rediscover(device: ZigBeeDevice)

        fun color(device: ZigBeeDevice)

        fun level(device: ZigBeeDevice, level: Float)
    }
}