package com.tunjid.rcswitchcontrol.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseRecyclerViewAdapter;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseViewHolder;

import java.util.List;

/**
 * Adapter for showing open NSD services
 * <p>
 * Created by tj.dahunsi on 2/4/17.
 */

public class ChatAdapter extends BaseRecyclerViewAdapter<ChatAdapter.TextViewHolder, ChatAdapter.ChatAdapterListener> {

    private List<String> responses;

    public ChatAdapter(ChatAdapterListener listener, List<String> list) {
        super(listener);
        this.responses = list;
    }

    @Override
    public TextViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.viewholder_responses, parent, false);
        return new TextViewHolder(view);
    }

    @Override
    public void onBindViewHolder(TextViewHolder holder, int position) {
        holder.bind(responses.get(position), adapterListener);
    }

    @Override
    public int getItemCount() {
        return responses.size();
    }

    public interface ChatAdapterListener extends BaseRecyclerViewAdapter.AdapterListener {
        void onTextClicked(String text);
    }

    static class TextViewHolder extends BaseViewHolder<ChatAdapterListener>
            implements View.OnClickListener {

        String text;
        TextView textView;

        TextViewHolder(View itemView) {
            super(itemView);
            textView = (TextView) itemView.findViewById(R.id.text);
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
