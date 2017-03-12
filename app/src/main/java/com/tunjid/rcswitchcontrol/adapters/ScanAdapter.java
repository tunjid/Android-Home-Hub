package com.tunjid.rcswitchcontrol.adapters;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


import com.tunjid.rcswitchcontrol.R;

import java.util.List;

/**
 * Adapter for BLE devices found while sacnning
 */
public class ScanAdapter extends RecyclerView.Adapter<ScanAdapter.ViewHolder> {

    private static final int BLE_DEVICE = 1;

    private List<BluetoothDevice> bleDevices;
    private AdapterListener adapterListener;

    public ScanAdapter(AdapterListener adapterListener, List<BluetoothDevice> bleDevices) {
        this.adapterListener = adapterListener;
        this.bleDevices = bleDevices;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        Context context = viewGroup.getContext();
        View itemView = LayoutInflater.from(context).inflate(R.layout.viewholder_scan, viewGroup, false);

        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {
        viewHolder.bind(bleDevices.get(position), adapterListener);
    }

    @Override
    public int getItemViewType(int position) {
        return BLE_DEVICE;
    }

    @Override
    public int getItemCount() {
        return bleDevices.size();
    }

    // ViewHolder for actual content
    static class ViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener {

        TextView deviceName;
        TextView deviceAddress;

        BluetoothDevice device;
        AdapterListener adapterListener;

        ViewHolder(View itemView) {
            super(itemView);

            deviceAddress = (TextView) itemView.findViewById(R.id.device_address);
            deviceName = (TextView) itemView.findViewById(R.id.device_name);
            itemView.setOnClickListener(this);
        }

        void bind(BluetoothDevice device, AdapterListener adapterListener) {
            this.device = device;
            this.adapterListener = adapterListener;

            String name = device.getName();

            if (TextUtils.isEmpty(name)) deviceName.setText(name);
            else deviceName.setText(R.string.unknown_device);

            deviceAddress.setText(device.getAddress());
        }

        @Override
        public void onClick(View v) {
            switch ((v.getId())) {
                case R.id.row_parent:
                    adapterListener.onBluetoothDeviceClicked(device);
                    break;
            }
        }
    }

    public interface AdapterListener {
        void onBluetoothDeviceClicked(BluetoothDevice bluetoothDevice);
    }

}
