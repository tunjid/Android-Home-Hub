package com.tunjid.rcswitchcontrol.adapters

import android.view.ViewGroup
import com.tunjid.androidbootstrap.recyclerview.InteractiveAdapter
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.data.Device
import com.tunjid.rcswitchcontrol.data.RcSwitch
import com.tunjid.rcswitchcontrol.data.ZigBeeDevice

typealias ViewHolder =  DeviceViewHolder<out InteractiveAdapter.AdapterListener, out Device>

class RemoteSwitchAdapter(
        switchListener: Listener,
        private val switches: List<Device>
) : InteractiveAdapter<ViewHolder, RemoteSwitchAdapter.Listener>(switchListener) {

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder = when (viewType) {
        RF_DEVICE -> RfDeviceViewHolder(getItemView(R.layout.viewholder_remote_switch, viewGroup), adapterListener)
        ZIGBEE_DEVICE -> ZigBeeDeviceViewHolder(getItemView(R.layout.viewholder_remote_switch, viewGroup), adapterListener)
        else -> throw IllegalArgumentException("Invalid object type")
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = when (val device = switches[position]) {
        is RcSwitch -> (holder as RfDeviceViewHolder).bind(device)
        is ZigBeeDevice -> (holder as ZigBeeDeviceViewHolder).bind(device)
        else -> throw IllegalArgumentException("Invalid object type")
    }

    override fun getItemViewType(position: Int): Int = when (switches[position]) {
        is RcSwitch -> RF_DEVICE
        is ZigBeeDevice -> ZIGBEE_DEVICE
        else -> throw IllegalArgumentException("Invalid object type")
    }

    override fun getItemCount(): Int = switches.size

    override fun getItemId(position: Int): Long = switches[position].hashCode().toLong()

    interface Listener : AdapterListener, RfDeviceViewHolder.Listener, ZigBeeDeviceViewHolder.Listener

    companion object {

        private const val RF_DEVICE = 1
        private const val ZIGBEE_DEVICE = 2

    }

}
