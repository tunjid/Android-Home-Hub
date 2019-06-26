package com.tunjid.rcswitchcontrol.adapters

import android.view.View
import android.widget.TextView
import com.tunjid.androidbootstrap.recyclerview.InteractiveAdapter
import com.tunjid.androidbootstrap.recyclerview.InteractiveViewHolder
import com.tunjid.rcswitchcontrol.R

// ViewHolder for actual content
open class DeviceViewHolder<T : InteractiveAdapter.AdapterListener, S : Any> internal constructor(
        itemView: View,
        listener: T
) : InteractiveViewHolder<T>(itemView, listener) {

    open lateinit var device: S

    open var deviceName: TextView = itemView.findViewById(R.id.switch_name)
    open var offSwitch: View = itemView.findViewById(R.id.off_switch)
    open var onSwitch: View = itemView.findViewById(R.id.on_switch)

    open fun bind(device: S) {
        this.device = device

    }
}