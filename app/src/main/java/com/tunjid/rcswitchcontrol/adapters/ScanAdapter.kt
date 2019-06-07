package com.tunjid.rcswitchcontrol.adapters

import android.bluetooth.BluetoothDevice
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.tunjid.androidbootstrap.communications.bluetooth.ScanResultCompat
import com.tunjid.androidbootstrap.recyclerview.InteractiveAdapter
import com.tunjid.androidbootstrap.recyclerview.InteractiveViewHolder
import com.tunjid.rcswitchcontrol.R

/**
 * Adapter for BLE devices found while sacnning
 */
class ScanAdapter(
        adapterListener: AdapterListener,
        private val scanResults: List<ScanResultCompat>
) : InteractiveAdapter<ScanAdapter.ViewHolder, ScanAdapter.AdapterListener>(adapterListener) {

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(getItemView(R.layout.viewholder_scan, viewGroup), adapterListener)

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) =
            viewHolder.bind(scanResults[position])

    override fun getItemViewType(position: Int): Int = BLE_DEVICE

    override fun getItemCount(): Int = scanResults.size

    // ViewHolder for actual content
    class ViewHolder internal constructor(
            itemView: View,
            adapterListener: AdapterListener
    ) : InteractiveViewHolder<AdapterListener>(itemView, adapterListener) {

        private var deviceName: TextView = itemView.findViewById(R.id.device_name)
        private var deviceAddress: TextView = itemView.findViewById(R.id.device_address)

        private lateinit var result: ScanResultCompat

        init {
            itemView.setOnClickListener { adapterListener.onBluetoothDeviceClicked(result.device) }
        }

        internal fun bind(result: ScanResultCompat) {
            this.result = result

            deviceAddress.text = if (result.scanRecord != null) result.scanRecord!!.deviceName else ""
            deviceName.text = if (result.device != null) result.device.address else ""
        }

    }

    interface AdapterListener : InteractiveAdapter.AdapterListener {
        fun onBluetoothDeviceClicked(bluetoothDevice: BluetoothDevice)
    }

    companion object {

        private const val BLE_DEVICE = 1
    }
}
