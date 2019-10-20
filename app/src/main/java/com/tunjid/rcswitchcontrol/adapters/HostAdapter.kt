/*
 * MIT License
 *
 * Copyright (c) 2019 Adetunji Dahunsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tunjid.rcswitchcontrol.adapters

import android.net.nsd.NsdServiceInfo
import android.text.SpannableStringBuilder
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.text.scale
import com.tunjid.androidx.recyclerview.InteractiveAdapter
import com.tunjid.androidx.recyclerview.InteractiveViewHolder
import com.tunjid.androidx.view.util.inflate
import com.tunjid.rcswitchcontrol.R

/**
 * Adapter for showing open NSD services
 *
 *
 * Created by tj.dahunsi on 2/4/17.
 */

class HostAdapter(
        listener: ServiceClickedListener,
        private val hosts: List<NsdServiceInfo>
) : InteractiveAdapter<HostAdapter.NSDViewHolder, HostAdapter.ServiceClickedListener>(listener) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NSDViewHolder {
        return NSDViewHolder(parent.inflate(R.layout.viewholder_nsd_list), delegate)
    }

    override fun onBindViewHolder(holder: NSDViewHolder, position: Int) =
            holder.bind(hosts[position], delegate)

    override fun getItemCount(): Int = hosts.size

    interface ServiceClickedListener {
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
            itemView.setOnClickListener { delegate?.onServiceClicked(serviceInfo) }
        }

        internal fun bind(info: NsdServiceInfo, listener: ServiceClickedListener) {
            serviceInfo = info
            delegate = listener

            textView.text = SpannableStringBuilder()
                    .append(info.serviceName)
                    .apply { if (delegate?.isSelf(info) == true) append(" (SELF)") }
                    .append("\n")
                    .scale(0.8F) { append(info.host.hostAddress) }
        }

    }
}
