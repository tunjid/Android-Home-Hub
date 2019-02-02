package com.tunjid.rcswitchcontrol.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.tunjid.androidbootstrap.view.recyclerview.InteractiveAdapter;
import com.tunjid.androidbootstrap.view.recyclerview.InteractiveViewHolder;
import com.tunjid.rcswitchcontrol.R;


import java.util.List;

import androidx.annotation.NonNull;

public class ChatAdapter extends InteractiveAdapter<ChatAdapter.TextViewHolder, ChatAdapter.ChatAdapterListener> {

    private List<String> responses;

    public ChatAdapter(ChatAdapterListener listener, List<String> list) {
        super(listener);
        this.responses = list;
    }

    @NonNull @Override
    public TextViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TextViewHolder(getItemView(R.layout.viewholder_responses, parent));
    }

    @Override
    public void onBindViewHolder(@NonNull TextViewHolder holder, int position) {
        holder.bind(responses.get(position), adapterListener);
    }

    @Override
    public int getItemCount() {
        return responses.size();
    }

    public interface ChatAdapterListener extends InteractiveAdapter.AdapterListener {
        void onTextClicked(String text);
    }

    static class TextViewHolder extends InteractiveViewHolder<ChatAdapterListener>
            implements View.OnClickListener {

        String text;
        TextView textView;

        TextViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.text);
            textView.setOnClickListener(this);
        }

        void bind(String text, ChatAdapterListener listener) {
            this.text = text;

            textView.setText(text);
            adapterListener = listener;

            textView.setClickable(adapterListener != null);
        }

        @Override
        public void onClick(View v) {
            if (adapterListener != null) adapterListener.onTextClicked(text);
        }
    }
}
