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

package com.tunjid.rcswitchcontrol.viewholders

import android.net.nsd.NsdServiceInfo
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tunjid.androidx.core.text.appendNewLine
import com.tunjid.androidx.core.text.scale

interface ServiceClickedListener {
    fun onServiceClicked(serviceInfo: NsdServiceInfo)

    fun isSelf(serviceInfo: NsdServiceInfo): Boolean
}

class HostScanViewHolder internal constructor(
        itemView: View,
        private val listener: ServiceClickedListener
) : RecyclerView.ViewHolder(itemView) {

    private lateinit var serviceInfo: NsdServiceInfo
    private val textView: TextView = itemView as TextView

    init {
        itemView.setOnClickListener { listener.onServiceClicked(serviceInfo) }
    }

    internal fun bind(info: NsdServiceInfo) {
        serviceInfo = info

        textView.text = SpannableStringBuilder()
                .append(info.serviceName)
                .apply { if (listener.isSelf(info)) append(" (SELF)") }
                .appendNewLine()
                .append(info.host.hostAddress.scale(0.8F))
    }

}