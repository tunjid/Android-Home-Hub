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

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rcswitchcontrol.zigbee.models.ZigBeeCommand
import com.rcswitchcontrol.zigbee.models.ZigBeeInput
import com.rcswitchcontrol.zigbee.models.ZigBeeNode
import com.tunjid.androidx.recyclerview.viewbinding.BindingViewHolder
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderDelegate
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderFrom
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.control.Device
import com.tunjid.rcswitchcontrol.control.Throttle
import com.tunjid.rcswitchcontrol.control.color
import com.tunjid.rcswitchcontrol.control.isCoordinator
import com.tunjid.rcswitchcontrol.control.isOn
import com.tunjid.rcswitchcontrol.control.level
import com.tunjid.rcswitchcontrol.control.throttleColorChanges
import com.tunjid.rcswitchcontrol.databinding.ViewholderZigbeeDeviceBinding
import com.tunjid.rcswitchcontrol.utils.makeAccessibleForTV

interface ZigBeeDeviceListener : DeviceLongClickListener {
    fun send(command: ZigBeeCommand)
}

private var BindingViewHolder<ViewholderZigbeeDeviceBinding>.device by viewHolderDelegate<Device.ZigBee>()
private var BindingViewHolder<ViewholderZigbeeDeviceBinding>.listener by viewHolderDelegate<ZigBeeDeviceListener>()
private val BindingViewHolder<ViewholderZigbeeDeviceBinding>.diagnosticOptions
    get() = listOfNotNull(
        R.string.zigbee_diagnostic_node to ZigBeeInput.Node,
        R.string.zigbee_diagnostic_rediscover to ZigBeeInput.Rediscover,
        (R.string.zigbee_diagnostic_enable_join to ZigBeeInput.Join(duration = 60))
            .takeIf { device.isCoordinator },
        (R.string.zigbee_diagnostic_disable_join to ZigBeeInput.Join(duration = null))
            .takeIf { device.isCoordinator },
    )
        .plus(device.node.supportedFeatures.map { it.nameRes to ZigBeeInput.Read(it) })
        .map {
            val context = itemView.context
            when (it.second) {
                is ZigBeeInput.Read -> context.getString(R.string.zigbee_diagnostic_read_attribute, context.getString(it.first))
                else -> context.getString(it.first)
            } to device.node.command(it.second)
        }

fun BindingViewHolder<ViewholderZigbeeDeviceBinding>.bind(device: Device.ZigBee) {
    this.device = device
    highlight(device)

    binding.apply {
        switchName.text = device.name
        toggle.isVisible = device.node.supports(ZigBeeNode.Feature.OnOff)
        leveler.isVisible = device.node.supports(ZigBeeNode.Feature.Level)
        colorPicker.isVisible = device.node.supports(ZigBeeNode.Feature.Color)

        device.isOn.let(toggle::setChecked)
        device.level?.let(leveler::setValue)
        device.color?.let(ColorStateList::valueOf)?.let(colorPicker::setIconTint)
    }
}

fun ViewGroup.zigbeeDeviceViewHolder(
    listener: ZigBeeDeviceListener
) = viewHolderFrom(ViewholderZigbeeDeviceBinding::inflate).apply binding@{
    this.listener = listener
    val throttle = Throttle { listener.send(device.node.command(ZigBeeInput.Level(level = it / 100F))) }

    binding.apply {
        cardView.makeAccessibleForTV(stroked = true)
        itemView.setOnClickListener { listener.onClicked(device) }
        itemView.setOnLongClickListener {
            listener.onLongClicked(device)
            true
        }

        toggle.makeAccessibleForTV()
        toggle.setOnClickListener { listener.send(device.node.command(ZigBeeInput.Toggle(isOn = toggle.isChecked))) }

        leveler.makeAccessibleForTV()
        leveler.addOnChangeListener { _, value, fromUser -> if (fromUser) throttle.run(value.toInt()) }

        zigbeeIcon.makeAccessibleForTV(stroked = true)
        zigbeeIcon.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.zigbee_diagnostic_title)
                .setItems(diagnosticOptions.map { it.first }.toTypedArray()) { _, index ->
                    listener.send(diagnosticOptions[index].second)
                }
                .show()
        }

        colorPicker.makeAccessibleForTV(stroked = true)
        colorPicker.setOnClickListener {
            ColorPickerDialogBuilder
                .with(context)
                .setTitle(R.string.color_picker_choose)
                .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
                .showLightnessSlider(true)
                .showAlphaSlider(false)
                .initialColor(device.color ?: Color.WHITE)
                .showColorEdit(true)
                .density(12)
                .throttleColorChanges { rgb ->
                    listener.send(device.node.command(ZigBeeInput.Color(rgb)))
                }
                .setPositiveButton("ok") {_, rgb, _ ->
                    listener.send(device.node.command(ZigBeeInput.Color(rgb)))
                }
                .setNegativeButton("cancel") {_, _ -> }
                .build()
                .show()
        }
    }
}
