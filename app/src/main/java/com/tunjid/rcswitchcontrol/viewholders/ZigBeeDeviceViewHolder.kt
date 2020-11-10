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
import androidx.core.view.isVisible
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rcswitchcontrol.zigbee.models.ZigBeeCommand
import com.rcswitchcontrol.zigbee.models.ZigBeeDevice
import com.rcswitchcontrol.zigbee.models.ZigBeeInput
import com.tunjid.androidx.recyclerview.viewbinding.BindingViewHolder
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderFrom
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.databinding.ViewholderZigbeeDeviceBinding
import com.tunjid.rcswitchcontrol.dialogfragments.Throttle
import com.tunjid.rcswitchcontrol.dialogfragments.throttleColorChanges

interface ZigBeeDeviceListener : DeviceLongClickListener {
    fun send(command: ZigBeeCommand)
}

private var BindingViewHolder<ViewholderZigbeeDeviceBinding>.device by BindingViewHolder.Prop<ZigBeeDevice>()
private var BindingViewHolder<ViewholderZigbeeDeviceBinding>.listener by BindingViewHolder.Prop<ZigBeeDeviceListener>()
private val BindingViewHolder<ViewholderZigbeeDeviceBinding>.diagnosticOptions
    get() = listOf(
            R.string.zigbee_diagnostic_node to ZigBeeInput.Node,
            R.string.zigbee_diagnostic_rediscover to ZigBeeInput.Rediscover
    )
            .plus(device.supportedFeatures.map { it.nameRes to ZigBeeInput.Read(it) })
            .map {
                val context = itemView.context
                when (it.second) {
                    is ZigBeeInput.Read -> context.getString(R.string.zigbee_diagnostic_read_attribute, context.getString(it.first))
                    else -> context.getString(it.first)
                } to device.command(it.second)
            }

fun BindingViewHolder<ViewholderZigbeeDeviceBinding>.bind(device: ZigBeeDevice) {
    this.device = device
    device.highlightViewHolder(this, listener::isSelected)

    binding.apply {
        switchName.text = device.name
        onSwitch.isVisible = device.supports(ZigBeeDevice.Feature.OnOff)
        offSwitch.isVisible = device.supports(ZigBeeDevice.Feature.OnOff)
        leveler.isVisible = device.supports(ZigBeeDevice.Feature.Level)
        colorPicker.isVisible = device.supports(ZigBeeDevice.Feature.Color)
    }
}

fun ViewGroup.zigbeeDeviceViewHolder(
        listener: ZigBeeDeviceListener
) = viewHolderFrom(ViewholderZigbeeDeviceBinding::inflate).apply binding@{
    this.listener = listener
    val throttle = Throttle { listener.send(device.command(ZigBeeInput.Level(level = it / 100F))) }

    binding.apply {
        itemView.setOnClickListener { listener.onClicked(device) }
        itemView.setOnLongClickListener {
            device.highlightViewHolder(this@binding, listener::onLongClicked)
            true
        }

        offSwitch.setOnClickListener { listener.send(device.command(ZigBeeInput.Toggle(isOn = false))) }
        onSwitch.setOnClickListener { listener.send(device.command(ZigBeeInput.Toggle(isOn = true))) }
        leveler.addOnChangeListener { _, value, fromUser -> if (fromUser) throttle.run(value.toInt()) }
        zigbeeIcon.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.zigbee_diagnostic_title)
                    .setItems(diagnosticOptions.map { it.first }.toTypedArray()) { _, index ->
                        listener.send(diagnosticOptions[index].second)
                    }
                    .show()
        }
        colorPicker.setOnClickListener {
            ColorPickerDialogBuilder
                    .with(context)
                    .setTitle(R.string.color_picker_choose)
                    .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
                    .showLightnessSlider(true)
                    .showAlphaSlider(false)
                    .density(12)
                    .throttleColorChanges { rgb ->
                        listener.send(device.command(ZigBeeInput.Color(rgb)))
                    }
                    .build()
                    .show()
        }
    }
}
