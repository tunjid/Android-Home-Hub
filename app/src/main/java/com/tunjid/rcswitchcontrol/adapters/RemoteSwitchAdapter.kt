package com.tunjid.rcswitchcontrol.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.tunjid.androidbootstrap.recyclerview.InteractiveAdapter
import com.tunjid.androidbootstrap.recyclerview.InteractiveViewHolder
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.model.RcSwitch


class RemoteSwitchAdapter(
        switchListener: SwitchListener,
        private val switches: List<RcSwitch>
) : InteractiveAdapter<RemoteSwitchAdapter.ViewHolder, RemoteSwitchAdapter.SwitchListener>(switchListener) {

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(getItemView(R.layout.viewholder_remote_switch, viewGroup), adapterListener)

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) =
            viewHolder.bind(switches[position])

    override fun getItemViewType(position: Int): Int = BLE_DEVICE

    override fun getItemCount(): Int = switches.size

    override fun getItemId(position: Int): Long = switches[position].hashCode().toLong()

    // ViewHolder for actual content
    class ViewHolder internal constructor(
            itemView: View,
            listener: SwitchListener
    ) : InteractiveViewHolder<SwitchListener>(itemView, listener) {

        private var deviceName: TextView = itemView.findViewById(R.id.switch_name)
        private var offSwitch: View = itemView.findViewById(R.id.off_switch)
        private var onSwitch: View = itemView.findViewById(R.id.on_switch)

        private lateinit var rcSwitch: RcSwitch

        internal fun bind(rcSwitch: RcSwitch) {
            this.rcSwitch = rcSwitch

            deviceName.text = rcSwitch.name

            offSwitch.setOnClickListener { adapterListener.onSwitchToggled(rcSwitch, false) }
            onSwitch.setOnClickListener { adapterListener.onSwitchToggled(rcSwitch, true) }
            itemView.setOnLongClickListener {
                adapterListener.onLongClicked(rcSwitch)
                true
            }
        }
    }

    interface SwitchListener : AdapterListener {
        fun onLongClicked(rcSwitch: RcSwitch)

        fun onSwitchToggled(rcSwitch: RcSwitch, state: Boolean)
    }

    companion object {

        private const val BLE_DEVICE = 1
    }

}
