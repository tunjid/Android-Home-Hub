package com.tunjid.rcswitchcontrol.fragments;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.snackbar.Snackbar;
import com.tunjid.androidbootstrap.core.components.ServiceConnection;
import com.tunjid.androidbootstrap.view.animator.ViewHider;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BroadcastReceiverFragment;
import com.tunjid.rcswitchcontrol.activities.MainActivity;
import com.tunjid.rcswitchcontrol.adapters.RemoteSwitchAdapter;
import com.tunjid.rcswitchcontrol.dialogfragments.NameServiceDialogFragment;
import com.tunjid.rcswitchcontrol.dialogfragments.RenameSwitchDialogFragment;
import com.tunjid.rcswitchcontrol.model.RcSwitch;
import com.tunjid.rcswitchcontrol.services.ClientBleService;
import com.tunjid.rcswitchcontrol.services.ServerNsdService;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Stack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import static android.content.Context.MODE_PRIVATE;
import static com.tunjid.rcswitchcontrol.App.isServiceRunning;
import static com.tunjid.rcswitchcontrol.model.RcSwitch.SWITCH_PREFS;
import static com.tunjid.rcswitchcontrol.services.ClientBleService.ACTION_CONTROL;
import static com.tunjid.rcswitchcontrol.services.ClientBleService.ACTION_SNIFFER;
import static com.tunjid.rcswitchcontrol.services.ClientBleService.BLUETOOTH_DEVICE;
import static com.tunjid.rcswitchcontrol.services.ServerNsdService.SERVICE_NAME_KEY;
import static java.util.Objects.requireNonNull;

public class ClientBleFragment extends BroadcastReceiverFragment
        implements
        //ServiceConnection,
        View.OnClickListener,
        RemoteSwitchAdapter.SwitchListener,
        RenameSwitchDialogFragment.SwitchNameListener,
        NameServiceDialogFragment.ServiceNameListener {

    private static final String TAG = ClientBleFragment.class.getSimpleName();

    private int lastOffSet;
    private boolean isDeleting;

    private BluetoothDevice bluetoothDevice;

    private View progressBar;
    private Button sniffButton;
    private TextView connectionStatus;
    private RecyclerView switchList;

    private ViewHider viewHider;

    private List<RcSwitch> switches;

    private RcSwitch.SwitchCreator switchCreator;

    private final ServiceConnection<ClientBleService> bleConnection = new ServiceConnection<>(
            ClientBleService.class,
            service -> onConnectionStateChanged(service.getConnectionState())
    );

    private final ServiceConnection<ServerNsdService> serverConnection = new ServiceConnection<>(
            ServerNsdService.class,
            service -> requireActivity().invalidateOptionsMenu()
    );

    public static ClientBleFragment newInstance(BluetoothDevice bluetoothDevice) {
        ClientBleFragment fragment = new ClientBleFragment();
        Bundle args = new Bundle();
        args.putParcelable(BLUETOOTH_DEVICE, bluetoothDevice);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        bluetoothDevice = Objects.requireNonNull(getArguments()).getParcelable(BLUETOOTH_DEVICE);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        switchCreator = new RcSwitch.SwitchCreator();
        switches = RcSwitch.getSavedSwitches();

        View rootView = inflater.inflate(R.layout.fragment_ble_client, container, false);
        AppBarLayout appBarLayout = rootView.findViewById(R.id.app_bar_layout);
        ItemTouchHelper helper = new ItemTouchHelper(swipeCallBack);

        sniffButton = rootView.findViewById(R.id.sniff);
        progressBar = rootView.findViewById(R.id.progress_bar);

        connectionStatus = rootView.findViewById(R.id.connection_status);
        switchList = rootView.findViewById(R.id.switch_list);

        viewHider = ViewHider.of(rootView.findViewById(R.id.button_container))
                .setDuration(ViewHider.BOTTOM).build();

        sniffButton.setOnClickListener(this);

        helper.attachToRecyclerView(switchList);
        switchList.setAdapter(new RemoteSwitchAdapter(this, switches));
        switchList.setLayoutManager(new LinearLayoutManager(getActivity()));
        switchList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy == 0) return;
                if (dy > 0) viewHider.hide();
                else viewHider.show();
            }
        });

        appBarLayout.addOnOffsetChangedListener((appBarLayout1, verticalOffset) -> {
            if (verticalOffset == 0) return;
            if (verticalOffset > lastOffSet) viewHider.hide();
            else viewHider.show();

            lastOffSet = verticalOffset;
        });

        toggleSniffButton();
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getToolBar().setTitle(R.string.switches);

        Activity activity = requireActivity();

        Bundle extras = new Bundle();
        extras.putParcelable(BLUETOOTH_DEVICE, requireNonNull(getArguments()).getParcelable(BLUETOOTH_DEVICE));

        bleConnection.with(activity).setExtras(extras).bind();

        if (activity.getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE).getBoolean(ServerNsdService.SERVER_FLAG, false)) {
            serverConnection.with(activity).start();
            serverConnection.with(activity).bind();
            activity.invalidateOptionsMenu();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // If the service is already bound, there will be no service connection callback
        if (bleConnection.isBound()) {
            bleConnection.getBoundService().onAppForeGround();
            onConnectionStateChanged(bleConnection.getBoundService().getConnectionState());
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_ble_client, menu);

        if (bleConnection.isBound()) {
            ClientBleService clientBleService = bleConnection.getBoundService();
            menu.findItem(R.id.menu_connect).setVisible(!clientBleService.isConnected());
            menu.findItem(R.id.menu_disconnect).setVisible(clientBleService.isConnected());
        }
        menu.findItem(R.id.menu_start_nsd).setVisible(!isServiceRunning(ServerNsdService.class));
        menu.findItem(R.id.menu_restart_nsd).setVisible(isServiceRunning(ServerNsdService.class));

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (bleConnection.isBound()) {
            switch (item.getItemId()) {
                case R.id.menu_connect:
                    bleConnection.getBoundService().connect(bluetoothDevice);
                    return true;
                case R.id.menu_disconnect:
                    bleConnection.getBoundService().disconnect();
                    return true;
                case R.id.menu_start_nsd:
                    NameServiceDialogFragment.newInstance().show(getChildFragmentManager(), "");
                    break;
                case R.id.menu_restart_nsd:
                    if (serverConnection.isBound()) serverConnection.getBoundService().restart();
                    break;
                case R.id.menu_forget:
                    Activity activity = requireActivity();
                    LocalBroadcastManager.getInstance(activity)
                            .sendBroadcast(new Intent(ServerNsdService.ACTION_STOP));

                    ClientBleService clientBleService = bleConnection.getBoundService();

                    clientBleService.disconnect();
                    clientBleService.close();

                    activity.getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE).edit()
                            .remove(ClientBleService.LAST_PAIRED_DEVICE)
                            .remove(ServerNsdService.SERVER_FLAG).apply();

                    Intent intent = new Intent(activity, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    startActivity(intent);
                    activity.finish();
                    return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (bleConnection.isBound()) bleConnection.getBoundService().onAppBackground();
    }

    @Override
    public void onDestroyView() {
        switchList = null;
        progressBar = null;
        sniffButton = null;
        connectionStatus = null;

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        bleConnection.unbindService();
        serverConnection.unbindService();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.sniff:
                toggleProgress(true);

                if (bleConnection.isBound()) {
                    bleConnection.getBoundService().writeCharacteristicArray(
                            ClientBleService.C_HANDLE_CONTROL,
                            new byte[]{ClientBleService.STATE_SNIFFING});
                }
                break;
        }
    }

    @Override
    public void onLongClicked(RcSwitch rcSwitch) {
        RenameSwitchDialogFragment.newInstance(rcSwitch).show(getChildFragmentManager(), "");
    }

    @Override
    public void onSwitchToggled(RcSwitch rcSwitch, boolean state) {
        if (bleConnection.isBound()) {
            bleConnection.getBoundService().writeCharacteristicArray(
                    ClientBleService.C_HANDLE_TRANSMITTER,
                    rcSwitch.getTransmission(state));
        }
    }

    @Override
    public void onSwitchRenamed(RcSwitch rcSwitch) {
        getAdapter().notifyItemChanged(switches.indexOf(rcSwitch));
        RcSwitch.saveSwitches(switches);
    }

    @Override
    public void onServiceNamed(String name) {
        Activity activity = requireActivity();

        activity.getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE)
                .edit().putString(SERVICE_NAME_KEY, name)
                .putBoolean(ServerNsdService.SERVER_FLAG, true).apply();

        serverConnection.with(activity).start();
        serverConnection.with(activity).bind();
    }

    private void onConnectionStateChanged(String newState) {
        requireActivity().invalidateOptionsMenu();
        String text = null;
        switch (newState) {
            case ClientBleService.ACTION_GATT_CONNECTED:
                text = getString(R.string.connected);
                break;
            case ClientBleService.ACTION_GATT_CONNECTING:
                text = getString(R.string.connecting);
                break;
            case ClientBleService.ACTION_GATT_DISCONNECTED:
                text = getString(R.string.disconnected);
                break;
        }
        connectionStatus.setText(getResources().getString(R.string.connection_state, text));
    }

    private void toggleSniffButton() {
        String state = switchCreator.getState().equals(RcSwitch.ON_CODE)
                ? getString(R.string.on)
                : getString(R.string.off);
        sniffButton.setText(getResources().getString(R.string.sniff_code, state));
    }

    private void toggleProgress(boolean show) {
        TransitionManager.beginDelayedTransition((ViewGroup) sniffButton.getParent(), new AutoTransition());

        sniffButton.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
        progressBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }

    @NonNull
    private RecyclerView.Adapter getAdapter() {
        return Objects.requireNonNull(switchList.getAdapter());
    }

    @Override protected List<String> filters() {
        return Arrays.asList(
                ClientBleService.ACTION_GATT_CONNECTED,
                ClientBleService.ACTION_GATT_CONNECTING,
                ClientBleService.ACTION_GATT_DISCONNECTED,
                ClientBleService.ACTION_GATT_SERVICES_DISCOVERED,
                ClientBleService.ACTION_CONTROL,
                ClientBleService.ACTION_SNIFFER,
                ClientBleService.DATA_AVAILABLE_UNKNOWN
        );
    }

    @Override protected void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case ClientBleService.ACTION_GATT_CONNECTED:
            case ClientBleService.ACTION_GATT_CONNECTING:
            case ClientBleService.ACTION_GATT_DISCONNECTED:
                onConnectionStateChanged(action);
                break;
            case ACTION_CONTROL: {
                byte[] rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_CONTROL);

                toggleProgress(rawData[0] == 0);
                break;
            }
            case ACTION_SNIFFER: {
                byte[] rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_SNIFFER);

                switch (switchCreator.getState()) {
                    case RcSwitch.ON_CODE:
                        switchCreator.withOnCode(rawData);
                        break;
                    case RcSwitch.OFF_CODE:
                        RcSwitch rcSwitch = switchCreator.withOffCode(rawData);
                        rcSwitch.setName("Switch " + (switches.size() + 1));

                        if (switches.contains(rcSwitch)) return;
                        switches.add(rcSwitch);
                        getAdapter().notifyDataSetChanged();

                        RcSwitch.saveSwitches(switches);

                        break;
                }

                toggleSniffButton();
                toggleProgress(false);
                break;
            }
        }

        Log.i(TAG, "Received data for: " + action);
    }

    private ItemTouchHelper.SimpleCallback swipeCallBack = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

        @Override
        public int getDragDirs(@NonNull RecyclerView recyclerView,
                               @NonNull RecyclerView.ViewHolder viewHolder) {
            return 0;
        }

        @Override
        public int getSwipeDirs(@NonNull RecyclerView recyclerView,
                                @NonNull RecyclerView.ViewHolder viewHolder) {
            if (isDeleting) return 0;
            return super.getSwipeDirs(recyclerView, viewHolder);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            if (isDeleting) return;
            isDeleting = true;

            View root = getView();
            if (root == null) return;

            int position = viewHolder.getAdapterPosition();
            DeletionHandler deletionHandler = new DeletionHandler(position, switches.size());

            deletionHandler.push(switches.get(position));
            switches.remove(position);

            getAdapter().notifyItemRemoved(position);

            Snackbar.make(root, R.string.deleted_switch, Snackbar.LENGTH_LONG)
                    .addCallback(deletionHandler)
                    .setAction(R.string.undo, deletionHandler)
                    .show();
        }
    };

    /**
     * Handles queued deletion of a Switch
     */
    private class DeletionHandler extends Snackbar.Callback implements View.OnClickListener {

        int originalPosition;
        int originalListSize;

        private Stack<RcSwitch> deletedItems = new Stack<>();

        DeletionHandler(int originalPosition, int originalListSize) {
            this.originalPosition = originalPosition;
            this.originalListSize = originalListSize;
        }

        @Override
        public void onDismissed(Snackbar snackbar, int event) {
            isDeleting = false;
            RcSwitch.saveSwitches(switches);
        }

        @Override
        public void onClick(View v) {
            if (!deletedItems.isEmpty()) {
                switches.add(originalPosition, pop());
                getAdapter().notifyItemInserted(originalPosition);
            }
            isDeleting = false;
        }

        RcSwitch push(RcSwitch item) {
            return deletedItems.push(item);
        }

        RcSwitch pop() {
            return deletedItems.pop();
        }
    }
}
