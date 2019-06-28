package com.tunjid.rcswitchcontrol.adapters

import android.view.View
import com.tunjid.androidbootstrap.recyclerview.InteractiveAdapter
import com.tunjid.rcswitchcontrol.data.ZigBeeDevice

// ViewHolder for actual content
class ZigBeeDeviceViewHolder internal constructor(
        itemView: View,
        listener: ZigBeeDeviceViewHolder.Listener
) : DeviceViewHolder<ZigBeeDeviceViewHolder.Listener, ZigBeeDevice>(itemView, listener) {

    override fun bind(device: ZigBeeDevice) {
        super.bind(device)

        deviceName.text = device.name

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
    }
}