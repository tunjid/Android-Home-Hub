package com.tunjid.rcswitchcontrol.adapters

import android.net.nsd.NsdServiceInfo
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tunjid.androidbootstrap.recyclerview.InteractiveAdapter
import com.tunjid.androidbootstrap.recyclerview.InteractiveViewHolder
import com.tunjid.rcswitchcontrol.R

/**
 * Adapter for showing open NSD services
 *
 *
 * Created by tj.dahunsi on 2/4/17.
 */

class NSDAdapter(
        listener: ServiceClickedListener,
        private val infoList: List<NsdServiceInfo>
) : InteractiveAdapter<NSDAdapter.NSDViewHolder, NSDAdapter.ServiceClickedListener>(listener) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NSDViewHolder {
        return NSDViewHolder(getItemView(R.layout.viewholder_nsd_list, parent), adapterListener)
    }

    override fun onBindViewHolder(holder: NSDViewHolder, position: Int) =
            holder.bind(infoList[position], adapterListener)

    override fun getItemCount(): Int = infoList.size

    interface ServiceClickedListener : AdapterListener {
        fun onServiceClicked(serviceInfo: NsdServiceInfo)

        fun isSelf(serviceInfo: NsdServiceInfo): Boolean
    }

    class NSDViewHolder internal constructor(
            itemView: View,
            listener: ServiceClickedListener
    ) : InteractiveViewHolder<ServiceClickedListener>(itemView, listener) {

        private lateinit var serviceInfo: NsdServiceInfo
        private val textView: TextView = itemView as TextView

        init {
            itemView.setOnClickListener { adapterListener.onServiceClicked(serviceInfo) }
        }

        internal fun bind(info: NsdServiceInfo, listener: ServiceClickedListener) {
            serviceInfo = info
            adapterListener = listener

            val stringBuilder = StringBuilder()
            stringBuilder.append(info.serviceName).append("\n")
                    .append(info.host.hostAddress)

            val isSelf = adapterListener.isSelf(info)

            if (isSelf) stringBuilder.append(" (SELF)")

            val color = ContextCompat.getColor(itemView.context, if (isSelf)
                R.color.dark_grey
            else
                R.color.colorPrimary)

            textView.setTextColor(color)
            textView.text = stringBuilder.toString()
        }

    }
}
