package com.tunjid.rcswitchcontrol.adapters

import android.view.View
import com.tunjid.androidbootstrap.recyclerview.InteractiveAdapter
import com.tunjid.rcswitchcontrol.data.RcSwitch

// ViewHolder for actual content
class RfDeviceViewHolder internal constructor(
        itemView: View,
        listener: RfDeviceViewHolder.Listener
) : DeviceViewHolder<RfDeviceViewHolder.Listener, RcSwitch>(itemView, listener) {

    override fun bind(device: RcSwitch) {
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
        fun onLongClicked(rcSwitch: RcSwitch)

        fun onSwitchToggled(rcSwitch: RcSwitch, state: Boolean)
    }
}