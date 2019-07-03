package com.tunjid.rcswitchcontrol.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import com.tunjid.androidbootstrap.recyclerview.InteractiveAdapter
import com.tunjid.androidbootstrap.recyclerview.InteractiveViewHolder
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.data.Record

class ChatAdapter(
        private val responses: List<Record>,
        listener: ChatAdapterListener
        ) : InteractiveAdapter<ChatAdapter.TextViewHolder, ChatAdapter.ChatAdapterListener>(listener) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextViewHolder =
            TextViewHolder(getItemView(R.layout.viewholder_responses, parent), adapterListener)

    override fun onBindViewHolder(holder: TextViewHolder, position: Int) =
            holder.bind(responses[position])

    override fun getItemCount(): Int = responses.size

    interface ChatAdapterListener : AdapterListener {
        fun onRecordClicked(record: Record)
    }

    class TextViewHolder internal constructor(
            itemView: View,
            listener: ChatAdapterListener
    ) : InteractiveViewHolder<ChatAdapterListener>(itemView, listener) {

        private lateinit var record: Record
        private val textView: TextView = itemView.findViewById(R.id.text)

        init {
            textView.setOnClickListener { adapterListener?.onRecordClicked(record) }
        }

        internal fun bind(record: Record) {
            this.record = record

            textView.text = record.entry
            textView.isClickable = adapterListener != null
        }
    }
}
