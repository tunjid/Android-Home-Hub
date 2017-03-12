package com.tunjid.rcswitchcontrol.adapters;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.model.RfSwitch;

import java.util.List;

/**
 * Adapter for BLE devices found while sacnning
 */
public class RemoteSwitchAdapter extends RecyclerView.Adapter<RemoteSwitchAdapter.ViewHolder> {

    private static final int BLE_DEVICE = 1;

    private List<RfSwitch> switches;
    private SwitchListener switchListener;

    public RemoteSwitchAdapter(SwitchListener switchListener, List<RfSwitch> switches) {
        this.switchListener = switchListener;
        this.switches = switches;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        Context context = viewGroup.getContext();
        View itemView = LayoutInflater.from(context).inflate(R.layout.viewholder_remote_switch, viewGroup, false);

        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {
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
    static class ViewHolder extends RecyclerView.ViewHolder
            implements
            View.OnClickListener,
            View.OnLongClickListener {

        TextView deviceName;
        Switch toggle;

        RfSwitch rfSwitch;
        SwitchListener switchListener;

        ViewHolder(View itemView) {
            super(itemView);

            deviceName = (TextView) itemView.findViewById(R.id.switch_name);
            toggle = (Switch) itemView.findViewById(R.id.switch_toggle);

            toggle.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        void bind(RfSwitch rfSwitch, SwitchListener switchListener) {
            this.rfSwitch = rfSwitch;
            this.switchListener = switchListener;

            deviceName.setText(rfSwitch.getName());
        }

        @Override
        public void onClick(View v) {
            switch ((v.getId())) {
                case R.id.switch_toggle:
                    switchListener.onSwitchToggled(rfSwitch, toggle.isChecked());
                    break;
            }
        }

        @Override
        public boolean onLongClick(View v) {
            switchListener.onLongClicked(rfSwitch);
            return true;
        }
    }

    public interface SwitchListener {
        void onLongClicked(RfSwitch rfSwitch);
        void onSwitchToggled(RfSwitch rfSwitch, boolean state);
    }

}
