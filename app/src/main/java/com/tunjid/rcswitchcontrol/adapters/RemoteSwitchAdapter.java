package com.tunjid.rcswitchcontrol.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.model.RcSwitch;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


public class RemoteSwitchAdapter extends RecyclerView.Adapter<RemoteSwitchAdapter.ViewHolder> {

    private static final int BLE_DEVICE = 1;

    private List<RcSwitch> switches;
    private SwitchListener switchListener;

    public RemoteSwitchAdapter(SwitchListener switchListener, List<RcSwitch> switches) {
        this.switchListener = switchListener;
        this.switches = switches;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        Context context = viewGroup.getContext();
        View itemView = LayoutInflater.from(context).inflate(R.layout.viewholder_remote_switch, viewGroup, false);

        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, final int position) {
        viewHolder.bind(switches.get(position), switchListener);
    }

    @Override
    public int getItemViewType(int position) {
        return BLE_DEVICE;
    }

    @Override
    public int getItemCount() {
        return switches.size();
    }

    // ViewHolder for actual content
    public static class ViewHolder extends RecyclerView.ViewHolder {

        TextView deviceName;
        Switch toggle;

        RcSwitch rcSwitch;
        SwitchListener switchListener;

        ViewHolder(View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(R.id.switch_name);
            toggle = itemView.findViewById(R.id.switch_toggle);
        }

        void bind(RcSwitch rcSwitch, SwitchListener switchListener) {
            this.rcSwitch = rcSwitch;
            this.switchListener = switchListener;

            deviceName.setText(rcSwitch.getName());

            toggle.setOnClickListener(view -> switchListener.onSwitchToggled(rcSwitch, toggle.isChecked()));
            itemView.setOnLongClickListener(view -> {
                switchListener.onLongClicked(rcSwitch);
                return true;
            });
        }
    }

    public interface SwitchListener {
        void onLongClicked(RcSwitch rcSwitch);

        void onSwitchToggled(RcSwitch rcSwitch, boolean state);
    }

}
