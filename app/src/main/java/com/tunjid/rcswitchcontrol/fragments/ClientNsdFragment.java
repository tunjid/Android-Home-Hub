package com.tunjid.rcswitchcontrol.fragments;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
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
import com.tunjid.rcswitchcontrol.activities.MainActivity;
import com.tunjid.rcswitchcontrol.adapters.ChatAdapter;
import com.tunjid.rcswitchcontrol.adapters.RemoteSwitchAdapter;
import com.tunjid.rcswitchcontrol.model.Payload;
import com.tunjid.rcswitchcontrol.model.RcSwitch;
import com.tunjid.rcswitchcontrol.nsd.nsdprotocols.BleRcProtocol;
import com.tunjid.rcswitchcontrol.nsd.nsdprotocols.CommsProtocol;
import com.tunjid.rcswitchcontrol.services.ClientNsdService;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import static android.content.Context.MODE_PRIVATE;
import static com.tunjid.rcswitchcontrol.model.RcSwitch.SWITCH_PREFS;

public class ClientNsdFragment extends BaseFragment
        implements
        ServiceConnection,
        View.OnClickListener,
        ChatAdapter.ChatAdapterListener,
        RemoteSwitchAdapter.SwitchListener,
        RenameSwitchDialogFragment.SwitchNameListener {

    private static final String TAG = ClientNsdFragment.class.getSimpleName();

    private boolean isDeleting;

    private ClientNsdService clientNsdService;

    private TextView connectionStatus;
    private RecyclerView switchList;
    private RecyclerView commandsView;

    private List<RcSwitch> switches = new ArrayList<>();
    private List<String> commands = new ArrayList<>();
    private List<String> messageHistory = new ArrayList<>();

    private final IntentFilter clientNsdServiceFilter = new IntentFilter();
    private final BroadcastReceiver nsdUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            switch (action) {
                case ClientNsdService.ACTION_SOCKET_CONNECTED:
                    onConnectionStateChanged(action);
                    if (commands.isEmpty()) clientNsdService.sendMessage(CommsProtocol.PING);
                    break;
                case ClientNsdService.ACTION_SOCKET_DISCONNECTED:
                    onConnectionStateChanged(action);
                    break;
                case ClientNsdService.ACTION_SERVER_RESPONSE:
                    String serverResponse = intent.getStringExtra(ClientNsdService.DATA_SERVER_RESPONSE);
                    Payload payload = Payload.deserialize(serverResponse);

                    commands.clear();
                    commands.addAll(payload.getCommands());
                    commandsView.getAdapter().notifyDataSetChanged();

                    String key = payload.getKey();
                    boolean isBleRc = key.equals(BleRcProtocol.class.getName());

                    swapAdapter(isBleRc);

                    if (isBleRc) {
                        if (payload.getData() != null) {
                            switches.clear();
                            switches.addAll(RcSwitch.deserialize(payload.getData()));
                            switchList.getAdapter().notifyDataSetChanged();
                        }
                        //Snackbar.make(switchList, payload.getResponse(), Snackbar.LENGTH_SHORT).show();
                    }
                    else {
                        messageHistory.add(payload.getResponse());
                        switchList.getAdapter().notifyDataSetChanged();
                    }

                    break;
            }

            Log.i(TAG, "Received data for: " + action);
        }
    };

    public static ClientNsdFragment newInstance() {
        ClientNsdFragment fragment = new ClientNsdFragment();
        Bundle bundle = new Bundle();

        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        clientNsdServiceFilter.addAction(ClientNsdService.ACTION_SOCKET_CONNECTED);
        clientNsdServiceFilter.addAction(ClientNsdService.ACTION_SOCKET_CONNECTING);
        clientNsdServiceFilter.addAction(ClientNsdService.ACTION_SOCKET_DISCONNECTED);
        clientNsdServiceFilter.addAction(ClientNsdService.ACTION_SERVER_RESPONSE);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_nsd_client, container, false);

        connectionStatus = (TextView) rootView.findViewById(R.id.connection_status);
        switchList = (RecyclerView) rootView.findViewById(R.id.switch_list);
        commandsView = (RecyclerView) rootView.findViewById(R.id.commands);

        swapAdapter(false);
        switchList.setLayoutManager(new LinearLayoutManager(getActivity()));

        commandsView.setAdapter(new ChatAdapter(this, commands));
        commandsView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        ItemTouchHelper helper = new ItemTouchHelper(swipeCallBack);
        helper.attachToRecyclerView(switchList);

        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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

        // If the service is already bound, there will be no service connection callback
        if (clientNsdService != null) {
            clientNsdService.onAppForeGround();
            onConnectionStateChanged(clientNsdService.getConnectionState());
        }
        else {
            onConnectionStateChanged(ClientNsdService.ACTION_SOCKET_DISCONNECTED);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_fragment_nsd_control, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (clientNsdService != null) {
            switch (item.getItemId()) {
                case R.id.menu_forget:
                    if (clientNsdService != null) clientNsdService.stopSelf();

                    getActivity().getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE).edit()
                            .remove(ClientNsdService.LAST_CONNECTED_SERVICE).apply();

                    Intent intent = new Intent(getActivity(), MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    startActivity(intent);
                    getActivity().finish();

                    return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (clientNsdService != null) clientNsdService.onAppBackground();
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
        clientNsdService.onAppForeGround();

        onConnectionStateChanged(clientNsdService.getConnectionState());
        if (commands.isEmpty()) clientNsdService.sendMessage(CommsProtocol.PING);
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
    public void onTextClicked(String text) {
        if (clientNsdService != null) clientNsdService.sendMessage(text);
    }

    @Override
    public void onLongClicked(RcSwitch rcSwitch) {
        RenameSwitchDialogFragment.newInstance(rcSwitch).show(getChildFragmentManager(), "");
    }

    @Override
    public void onSwitchToggled(RcSwitch rcSwitch, boolean state) {
        if (clientNsdService == null) return;

        byte[] code = state ? rcSwitch.getOnCode() : rcSwitch.getOffCode();
        byte[] transmission = new byte[7];

        System.arraycopy(code, 0, transmission, 0, code.length);
        transmission[4] = rcSwitch.getPulseLength();
        transmission[5] = rcSwitch.getBitLength();
        transmission[6] = rcSwitch.getProtocol();

        clientNsdService.sendMessage(Base64.encodeToString(transmission, Base64.DEFAULT));
    }

    @Override
    public void onSwitchRenamed(RcSwitch rcSwitch) {
        //switchList.getAdapter().notifyItemChanged(switches.indexOf(rcSwitch));
        Snackbar.make(commandsView, "Not supported, change the name on the server device", Snackbar.LENGTH_SHORT).show();
    }

    private void onConnectionStateChanged(String newState) {
        getActivity().invalidateOptionsMenu();
        String text = null;
        switch (newState) {
            case ClientNsdService.ACTION_SOCKET_CONNECTED:
                text = getString(R.string.connected);
                break;
            case ClientNsdService.ACTION_SOCKET_CONNECTING:
                text = getString(R.string.connecting);
                break;
            case ClientNsdService.ACTION_SOCKET_DISCONNECTED:
                text = getString(R.string.disconnected);
                break;
        }
        connectionStatus.setText(getResources().getString(R.string.connection_state, text));
    }

    private void swapAdapter(boolean isSwitchAdapter) {
        Object adapter = switchList.getAdapter();

        if (isSwitchAdapter && adapter instanceof RemoteSwitchAdapter) return;
        if (!isSwitchAdapter && adapter instanceof ChatAdapter) return;

        switchList.setAdapter(isSwitchAdapter
                ? new RemoteSwitchAdapter(this, switches)
                : new ChatAdapter(null, messageHistory));
    }

    private ItemTouchHelper.SimpleCallback swipeCallBack = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

        @Override
        public int getDragDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            return 0;
        }

        @Override
        public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if (isDeleting || recyclerView.getAdapter() instanceof ChatAdapter) return 0;
            return super.getSwipeDirs(recyclerView, viewHolder);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            return false;
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
                switchList.getAdapter().notifyItemInserted(originalPosition);
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
