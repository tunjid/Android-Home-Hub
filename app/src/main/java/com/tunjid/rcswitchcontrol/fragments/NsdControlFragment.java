package com.tunjid.rcswitchcontrol.fragments;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment;
import com.tunjid.rcswitchcontrol.adapters.RemoteSwitchAdapter;
import com.tunjid.rcswitchcontrol.bluetooth.BluetoothLeService;
import com.tunjid.rcswitchcontrol.model.RfSwitch;
import com.tunjid.rcswitchcontrol.nsd.nsdprotocols.Payload;
import com.tunjid.rcswitchcontrol.nsd.services.ClientNsdService;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class NsdControlFragment extends BaseFragment
        implements
        ServiceConnection,
        View.OnClickListener,
        RemoteSwitchAdapter.SwitchListener,
        RenameSwitchDialogFragment.SwitchNameListener {

    private static final String TAG = NsdControlFragment.class.getSimpleName();

    private boolean isDeleting;

    private NsdServiceInfo service;
    private ClientNsdService clientNsdService;

    private TextView connectionStatus;
    private RecyclerView switchList;

    private ProgressDialog progressDialog;

    private List<RfSwitch> switches = new ArrayList<>();

    private final IntentFilter clientNsdServiceFilter = new IntentFilter();
    private final BroadcastReceiver nsdUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            switch (action) {
                case ClientNsdService.ACTION_SOCKET_CONNECTED:
                    if (progressDialog != null) progressDialog.dismiss();
                    break;
                case ClientNsdService.ACTION_SERVER_RESPONSE:
                    String response = intent.getStringExtra(ClientNsdService.DATA_SERVER_RESPONSE);
                    Payload payload = Payload.deserialize(response);

                    if (payload.getData() instanceof ArrayList) {

                    }

                    break;
            }

            Log.i(TAG, "Received data for: " + action);
        }
    };

    public static NsdControlFragment newInstance(NsdServiceInfo nsdServiceInfo) {
        NsdControlFragment fragment = new NsdControlFragment();
        Bundle bundle = new Bundle();

        bundle.putParcelable(ClientNsdService.NSD_SERVICE_INFO_KEY, nsdServiceInfo);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        clientNsdServiceFilter.addAction(ClientNsdService.ACTION_SOCKET_CONNECTED);
        clientNsdServiceFilter.addAction(ClientNsdService.ACTION_SERVER_RESPONSE);

        service = getArguments().getParcelable(ClientNsdService.NSD_SERVICE_INFO_KEY);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_control, container, false);

        connectionStatus = (TextView) rootView.findViewById(R.id.connection_status);
        switchList = (RecyclerView) rootView.findViewById(R.id.switch_list);

        switchList.setAdapter(new RemoteSwitchAdapter(this, switches));
        switchList.setLayoutManager(new LinearLayoutManager(getActivity()));

        ItemTouchHelper helper = new ItemTouchHelper(swipeCallBack);
        helper.attachToRecyclerView(switchList);

        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        progressDialog = ProgressDialog.show(getActivity(),
                getString(R.string.connection_title),
                getString(R.string.connection_text), true, true);

        LocalBroadcastManager.getInstance(getContext()).registerReceiver(nsdUpdateReceiver, clientNsdServiceFilter);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getToolBar().setTitle(R.string.switches);

        Intent clientIntent = new Intent(getActivity(), ClientNsdService.class);
        getActivity().bindService(clientIntent, this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onResume() {
        super.onResume();

//        // If the service is already bound, there will be no service connection callback
//        if (bluetoothLeService != null) {
//            bluetoothLeService.onAppForeGround();
//            onConnectionStateChanged(bluetoothLeService.getConnectionState());
//        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
//        inflater.inflate(R.menu.menu_fragment_control, menu);
//
//        if (bluetoothLeService != null) {
//            menu.findItem(R.id.menu_connect).setVisible(!bluetoothLeService.isConnected());
//            menu.findItem(R.id.menu_disconnect).setVisible(bluetoothLeService.isConnected());
//        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
//        if (bluetoothLeService != null) {
//            switch (item.getItemId()) {
//            }
//        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStop() {
        super.onStop();
//        if (bluetoothLeService != null) bluetoothLeService.onAppBackground();
    }

    @Override
    public void onDestroyView() {
        // Do not receive broadcasts when view is destroyed
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(nsdUpdateReceiver);

        switchList = null;
        connectionStatus = null;

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        getActivity().unbindService(this);
        super.onDestroy();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {

        clientNsdService = ((ClientNsdService.NsdClientBinder) binder).getClientService();
        clientNsdService.connect(service);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        clientNsdService = null;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.sniff:
                break;
        }
    }

    @Override
    public void onLongClicked(RfSwitch rfSwitch) {
        RenameSwitchDialogFragment.newInstance(rfSwitch).show(getChildFragmentManager(), "");
    }

    @Override
    public void onSwitchToggled(RfSwitch rfSwitch, boolean state) {
        if (clientNsdService == null) return;

        byte[] code = state ? rfSwitch.getOnCode() : rfSwitch.getOffCode();
        byte[] transmission = new byte[7];

        System.arraycopy(code, 0, transmission, 0, code.length);
        transmission[4] = rfSwitch.getPulseLength();
        transmission[5] = rfSwitch.getBitLength();
        transmission[6] = rfSwitch.getProtocol();

        clientNsdService.sendMessage(Base64.encodeToString(transmission, Base64.DEFAULT));
    }

    @Override
    public void onSwitchRenamed(RfSwitch rfSwitch) {
        switchList.getAdapter().notifyItemChanged(switches.indexOf(rfSwitch));
        // TODO
    }

    private void onConnectionStateChanged(String newState) {
        getActivity().invalidateOptionsMenu();
        String text = null;
        switch (newState) {
            case BluetoothLeService.ACTION_GATT_CONNECTED:
                text = getString(R.string.connected);
                break;
            case BluetoothLeService.ACTION_GATT_CONNECTING:
                text = getString(R.string.connecting);
                break;
            case BluetoothLeService.ACTION_GATT_DISCONNECTED:
                text = getString(R.string.disconnected);
                break;
        }
        connectionStatus.setText(getResources().getString(R.string.connection_state, text));
    }

    private ItemTouchHelper.SimpleCallback swipeCallBack = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if (isDeleting) return 0;
            return super.getSwipeDirs(recyclerView, viewHolder);
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

            if (isDeleting) return;
            isDeleting = true;

            View rootView = getView();

            if (rootView != null) {
                int position = viewHolder.getAdapterPosition();
                DeletionHandler deletionHandler = new DeletionHandler(position, switches.size());

                deletionHandler.push(switches.get(position));
                switches.remove(position);

                switchList.getAdapter().notifyItemRemoved(position);

                Snackbar.make(rootView, R.string.deleted_switch, Snackbar.LENGTH_LONG)
                        .addCallback(deletionHandler)
                        .setAction(R.string.undo, deletionHandler)
                        .show();
            }
        }
    };

    /**
     * Handles queued deletion of a Switch
     */
    private class DeletionHandler extends Snackbar.Callback implements View.OnClickListener {

        int originalPosition;
        int originalListSize;

        private Stack<RfSwitch> deletedItems = new Stack<>();

        DeletionHandler(int originalPosition, int originalListSize) {
            this.originalPosition = originalPosition;
            this.originalListSize = originalListSize;
        }

        @Override
        public void onDismissed(Snackbar snackbar, int event) {
            isDeleting = false;
            RfSwitch.saveSwitches(getContext(), switches);
        }

        @Override
        public void onClick(View v) {
            if (!deletedItems.isEmpty()) {
                switches.add(originalPosition, pop());
                switchList.getAdapter().notifyItemInserted(originalPosition);
            }
            isDeleting = false;
        }

        RfSwitch push(RfSwitch item) {
            return deletedItems.push(item);
        }

        RfSwitch pop() {
            return deletedItems.pop();
        }
    }
}
