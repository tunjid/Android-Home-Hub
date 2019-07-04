package com.tunjid.rcswitchcontrol.adapters

import android.view.View
import android.widget.SeekBar
import com.tunjid.androidbootstrap.recyclerview.InteractiveAdapter
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.data.ZigBeeDevice

// ViewHolder for actual content
class ZigBeeDeviceViewHolder internal constructor(
        itemView: View,
        listener: ZigBeeDeviceViewHolder.Listener
) : DeviceViewHolder<ZigBeeDeviceViewHolder.Listener, ZigBeeDevice>(itemView, listener) {

    private val colorPicker = itemView.findViewById<View>(R.id.color_picker)
    private val zigBeeIcon = itemView.findViewById<View>(R.id.zigbee_icon)
    private val leveler = itemView.findViewById<SeekBar>(R.id.leveler)

    override fun bind(device: ZigBeeDevice) {
        super.bind(device)

        deviceName.text = device.name

        leveler.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) =
                    adapterListener.level(device, progress/100F)

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })
        zigBeeIcon.setOnClickListener { adapterListener.rediscover(device) }
        colorPicker.setOnClickListener { adapterListener.color(device) }
        offSwitch.setOnClickListener { adapterListener.onSwitchToggled(device, false) }
        onSwitch.setOnClickListener { adapterListener.onSwitchToggled(device, true) }
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

        fun level(device: ZigBeeDevice, level: Float)
    }
}