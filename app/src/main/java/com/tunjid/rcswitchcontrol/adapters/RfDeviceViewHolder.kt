package com.tunjid.rcswitchcontrol.adapters

import android.view.View
import com.tunjid.androidbootstrap.recyclerview.InteractiveAdapter
import com.tunjid.rcswitchcontrol.data.RfSwitch

// ViewHolder for actual content
class RfDeviceViewHolder internal constructor(
        itemView: View,
        listener: RfDeviceViewHolder.Listener
) : DeviceViewHolder<RfDeviceViewHolder.Listener, RfSwitch>(itemView, listener) {

    override fun bind(device: RfSwitch) {
        super.bind(device)

        deviceName.text = device.name

        offSwitch.setOnClickListener { adapterListener.onSwitchToggled(device, false) }
        onSwitch.setOnClickListener { adapterListener.onSwitchToggled(device, true) }
        itemView.setOnLongClickListener {
            adapterListener.onLongClicked(device)
            true
        }
    }

    interface Listener : InteractiveAdapter.AdapterListener {
        fun onLongClicked(rfSwitch: RfSwitch)

        fun onSwitchToggled(rfSwitch: RfSwitch, state: Boolean)
    }
}