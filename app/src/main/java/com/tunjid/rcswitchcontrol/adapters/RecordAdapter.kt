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

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import com.tunjid.androidx.recyclerview.InteractiveAdapter
import com.tunjid.androidx.recyclerview.InteractiveViewHolder
import com.tunjid.androidx.view.util.inflate
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.data.Record

class RecordAdapter(
        private val responses: List<Record>,
        listener: ChatAdapterListener
) : InteractiveAdapter<ViewHolder, RecordAdapter.ChatAdapterListener>(listener) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when(viewType) {
        RECORD -> RecordViewHolder(parent.inflate(delegate.layoutRes), delegate)
        else -> object : InteractiveViewHolder<ChatAdapterListener>(parent.inflate(R.layout.viewholder_padding), delegate) {}
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (holder is RecordViewHolder) holder.bind(responses[position])
    }

    override fun getItemCount(): Int = responses.size

    override fun getItemViewType(position: Int): Int = RECORD

    interface ChatAdapterListener {
        val layoutRes: Int

        fun onRecordClicked(record: Record)
    }

    class RecordViewHolder internal constructor(
            itemView: View,
            listener: ChatAdapterListener
    ) : InteractiveViewHolder<ChatAdapterListener>(itemView, listener) {

        private lateinit var record: Record
        private val textView: TextView = itemView.findViewById(R.id.text)

        init {
            textView.setOnClickListener { delegate?.onRecordClicked(record) }
        }

        internal fun bind(record: Record) {
            this.record = record

            textView.text = record.entry
            textView.isClickable = delegate != null
        }
    }

    companion object {
        private const val RECORD = 1
    }
}
