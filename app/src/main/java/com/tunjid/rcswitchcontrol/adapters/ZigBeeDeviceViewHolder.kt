package com.tunjid.rcswitchcontrol.adapters

import android.view.View
import com.tunjid.androidbootstrap.recyclerview.InteractiveAdapter
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.data.ZigBeeDevice

// ViewHolder for actual content
class ZigBeeDeviceViewHolder internal constructor(
        itemView: View,
        listener: ZigBeeDeviceViewHolder.Listener
) : DeviceViewHolder<ZigBeeDeviceViewHolder.Listener, ZigBeeDevice>(itemView, listener) {

    val colorPicker = itemView.findViewById<View>(R.id.color_picker)

    override fun bind(device: ZigBeeDevice) {
        super.bind(device)

        deviceName.text = device.name

        colorPicker.setOnClickListener { adapterListener.color(device) }
        offSwitch.setOnClickListener { adapterListener.onSwitchToggled(device, false) }
        onSwitch.setOnClickListener { adapterListener.onSwitchToggled(device, true) }
        itemView.setOnClickListener { adapterListener.rediscover(device) }
        itemView.setOnLongClickListener {
            adapterListener.onLongClicked(device)
            true
        }
    }

    interface Listener : InteractiveAdapter.AdapterListener {
        fun onLongClicked(device: ZigBeeDevice)

        fun onSwitchToggled(device: ZigBeeDevice, state: Boolean)

        fun rediscover(device: ZigBeeDevice)

        fun color(device: ZigBeeDevice)
    }
}