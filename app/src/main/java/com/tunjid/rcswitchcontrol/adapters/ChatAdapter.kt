package com.tunjid.rcswitchcontrol.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import com.tunjid.androidbootstrap.recyclerview.InteractiveAdapter
import com.tunjid.androidbootstrap.recyclerview.InteractiveViewHolder
import com.tunjid.rcswitchcontrol.R

class ChatAdapter(
        private val responses: List<String>,
        listener: ChatAdapterListener
        ) : InteractiveAdapter<ChatAdapter.TextViewHolder, ChatAdapter.ChatAdapterListener>(listener) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextViewHolder =
            TextViewHolder(getItemView(R.layout.viewholder_responses, parent), adapterListener)

    override fun onBindViewHolder(holder: TextViewHolder, position: Int) =
            holder.bind(responses[position])

    override fun getItemCount(): Int = responses.size

    interface ChatAdapterListener : AdapterListener {
        fun onTextClicked(text: String)
    }

    class TextViewHolder internal constructor(
            itemView: View,
            listener: ChatAdapterListener
    ) : InteractiveViewHolder<ChatAdapterListener>(itemView, listener) {

        private lateinit var text: String
        private val textView: TextView = itemView.findViewById(R.id.text)

        init {
            textView.setOnClickListener { adapterListener?.onTextClicked(text) }
        }

        internal fun bind(text: String) {
            this.text = text

            textView.text = text
            textView.isClickable = adapterListener != null
        }
    }
}
