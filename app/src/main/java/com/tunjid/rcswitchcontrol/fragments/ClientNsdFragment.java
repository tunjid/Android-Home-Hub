package com.tunjid.rcswitchcontrol.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.flexbox.AlignItems;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.JustifyContent;
import com.google.android.material.snackbar.Snackbar;
import com.tunjid.androidbootstrap.core.components.ServiceConnection;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BroadcastReceiverFragment;
import com.tunjid.rcswitchcontrol.activities.MainActivity;
import com.tunjid.rcswitchcontrol.adapters.ChatAdapter;
import com.tunjid.rcswitchcontrol.adapters.RemoteSwitchAdapter;
import com.tunjid.rcswitchcontrol.dialogfragments.RenameSwitchDialogFragment;
import com.tunjid.rcswitchcontrol.model.Payload;
import com.tunjid.rcswitchcontrol.model.RcSwitch;
import com.tunjid.rcswitchcontrol.nsd.protocols.BleRcProtocol;
import com.tunjid.rcswitchcontrol.nsd.protocols.CommsProtocol;
import com.tunjid.rcswitchcontrol.services.ClientBleService;
import com.tunjid.rcswitchcontrol.services.ClientNsdService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static android.content.Context.MODE_PRIVATE;
import static com.tunjid.rcswitchcontrol.model.RcSwitch.SWITCH_PREFS;
import static java.util.Objects.requireNonNull;

public class ClientNsdFragment extends BroadcastReceiverFragment
        implements
        View.OnClickListener,
        ChatAdapter.ChatAdapterListener,
        RemoteSwitchAdapter.SwitchListener,
        RenameSwitchDialogFragment.SwitchNameListener {

    private static final String TAG = ClientNsdFragment.class.getSimpleName();

    private boolean isDeleting;

    private TextView connectionStatus;
    private RecyclerView switchList;
    private RecyclerView commandsView;

    private List<RcSwitch> switches = new ArrayList<>();
    private List<String> commands = new ArrayList<>();
    private List<String> messageHistory = new ArrayList<>();

    private ServiceConnection<ClientNsdService> nsdConnection;

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
        nsdConnection = new ServiceConnection<>(ClientNsdService.class, this::onServiceConnected);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_nsd_client, container, false);
        Context context = root.getContext();

        switchList = root.findViewById(R.id.switch_list);
        commandsView = root.findViewById(R.id.commands);
        connectionStatus = root.findViewById(R.id.connection_status);

        swapAdapter(false);

        FlexboxLayoutManager layoutManager = new FlexboxLayoutManager(context);
        layoutManager.setAlignItems(AlignItems.CENTER);
        layoutManager.setFlexDirection(FlexDirection.ROW);
        layoutManager.setJustifyContent(JustifyContent.FLEX_START);

        switchList.setLayoutManager(new LinearLayoutManager(context));

        commandsView.setAdapter(new ChatAdapter(this, commands));
        commandsView.setLayoutManager(layoutManager);

        ItemTouchHelper helper = new ItemTouchHelper(swipeCallBack);
        helper.attachToRecyclerView(switchList);

        return root;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getToolBar().setTitle(R.string.switches);

        nsdConnection.with(requireActivity()).bind();
    }

    @Override
    public void onResume() {
        super.onResume();

        // If the service is already bound, there will be no service connection callback
        if (nsdConnection.isBound()) {
            nsdConnection.getBoundService().onAppForeGround();
            onConnectionStateChanged(nsdConnection.getBoundService().getConnectionState());
        }
        else {
            onConnectionStateChanged(ClientNsdService.ACTION_SOCKET_DISCONNECTED);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_fragment_nsd_client, menu);
        menu.findItem(R.id.menu_connect).setVisible(nsdConnection.isBound() && !nsdConnection.getBoundService().isConnected());
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (nsdConnection.isBound()) {
            switch (item.getItemId()) {
                case R.id.menu_connect:
                    requireActivity().sendBroadcast(new Intent(ClientNsdService.ACTION_START_NSD_DISCOVERY));
                    onConnectionStateChanged(ClientNsdService.ACTION_SOCKET_CONNECTING);
                    return true;
                case R.id.menu_forget:
                    // Don't call unbind, when the hosting activity is finished,
                    // onDestroy will be called and the connection unbound
                    if (nsdConnection.isBound()) nsdConnection.getBoundService().stopSelf();

                    requireActivity().getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE).edit()
                            .remove(ClientNsdService.LAST_CONNECTED_SERVICE).apply();

                    Intent intent = new Intent(requireActivity(), MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    startActivity(intent);
                    requireActivity().finish();

                    return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (nsdConnection.isBound()) nsdConnection.getBoundService().onAppBackground();
    }

    @Override
    public void onDestroyView() {
        switchList = null;
        connectionStatus = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        nsdConnection.unbindService();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() != R.id.sniff) return;
        if (nsdConnection.isBound()) {
            nsdConnection.getBoundService().sendMessage(
                    Payload.builder().setAction(getString(R.string.scanblercprotocol_sniff)).build()
            );
        }
    }

    @Override
    public void onTextClicked(String text) {
        if (nsdConnection.isBound()) {
            nsdConnection.getBoundService().sendMessage(Payload.builder().setAction(text).build());
        }
    }

    @Override
    public void onLongClicked(RcSwitch rcSwitch) {
        RenameSwitchDialogFragment.newInstance(rcSwitch).show(getChildFragmentManager(), "");
    }

    @Override
    public void onSwitchToggled(RcSwitch rcSwitch, boolean state) {
        if (nsdConnection.isBound()) nsdConnection.getBoundService().sendMessage(
                Payload.builder().setAction(ClientBleService.ACTION_TRANSMITTER)
                        .setData(Base64.encodeToString(rcSwitch.getTransmission(state), Base64.DEFAULT))
                        .build()
        );
    }

    @Override
    public void onSwitchRenamed(RcSwitch rcSwitch) {
        getAdapter().notifyItemChanged(switches.indexOf(rcSwitch));
        if (nsdConnection.isBound()) nsdConnection.getBoundService().sendMessage(
                Payload.builder().setAction(getString(R.string.blercprotocol_rename_command))
                        .setData(rcSwitch.serialize())
                        .build()
        );
    }

    private void onServiceConnected(ClientNsdService service) {
        onConnectionStateChanged(service.getConnectionState());
        if (commands.isEmpty()) service.sendMessage(
                Payload.builder().setAction(CommsProtocol.PING).build()
        );
    }

    private void onConnectionStateChanged(String newState) {
        requireActivity().invalidateOptionsMenu();
        String text = null;
        switch (newState) {
            case ClientNsdService.ACTION_SOCKET_CONNECTED:
                text = !nsdConnection.isBound()
                        ? getString(R.string.connected)
                        : getResources().getString(R.string.connected_to, nsdConnection.getBoundService().getServiceName());
                break;
            case ClientNsdService.ACTION_SOCKET_CONNECTING:
                text = !nsdConnection.isBound()
                        ? getString(R.string.connecting)
                        : getResources().getString(R.string.connecting_to, nsdConnection.getBoundService().getServiceName());
                break;
            case ClientNsdService.ACTION_SOCKET_DISCONNECTED:
                text = getString(R.string.disconnected);
                break;
        }
        connectionStatus.setText(getResources().getString(R.string.connection_state, text));
    }

    private void swapAdapter(boolean isSwitchAdapter) {
        Object adapter = getAdapter();

        if (isSwitchAdapter && adapter instanceof RemoteSwitchAdapter) return;
        if (!isSwitchAdapter && adapter instanceof ChatAdapter) return;

        switchList.setAdapter(isSwitchAdapter
                ? new RemoteSwitchAdapter(this, switches)
                : new ChatAdapter(null, messageHistory));
    }

    private RecyclerView.Adapter getAdapter() {
        return switchList.getAdapter();
    }

    @Override protected List<String> filters() {
        return Arrays.asList(
                ClientNsdService.ACTION_SOCKET_CONNECTED,
                ClientNsdService.ACTION_SOCKET_CONNECTING,
                ClientNsdService.ACTION_SOCKET_DISCONNECTED,
                ClientNsdService.ACTION_SERVER_RESPONSE
        );
    }

    @Override protected void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case ClientNsdService.ACTION_SOCKET_CONNECTED:
                if (commands.isEmpty() && nsdConnection.isBound())
                    nsdConnection.getBoundService().sendMessage(
                            Payload.builder().setAction(CommsProtocol.PING).build()
                    );
                onConnectionStateChanged(action);
                requireActivity().invalidateOptionsMenu();
                break;
            case ClientNsdService.ACTION_SOCKET_CONNECTING:
            case ClientNsdService.ACTION_SOCKET_DISCONNECTED:
                onConnectionStateChanged(action);
                requireActivity().invalidateOptionsMenu();
                break;
            case ClientNsdService.ACTION_SERVER_RESPONSE:
                String serverResponse = intent.getStringExtra(ClientNsdService.DATA_SERVER_RESPONSE);
                Payload payload = Payload.deserialize(serverResponse);

                commands.clear();
                commands.addAll(payload.getCommands());
                requireNonNull(commandsView.getAdapter()).notifyDataSetChanged();

                String key = payload.getKey();
                boolean isBleRc = key.equals(BleRcProtocol.class.getName());

                swapAdapter(isBleRc);

                if (isBleRc && payload.getAction() != null) {
                    if (payload.getAction().equals(ClientBleService.ACTION_TRANSMITTER)) {
                        switches.clear();
                        switches.addAll(RcSwitch.deserializeSavedSwitches(payload.getData()));
                        getAdapter().notifyDataSetChanged();

                    }
//                        else if (payload.getAction().equals(ClientBleService.ACTION_CONTROL)) {
//                        }
//                        else if (payload.getAction().equals(ClientBleService.ACTION_SNIFFER)) {
//                        }
                    else if (payload.getAction().equals(getString(R.string.blercprotocol_delete_command))
                            || payload.getAction().equals(getString(R.string.blercprotocol_rename_command))) {
                        switches.clear();
                        switches.addAll(RcSwitch.deserializeSavedSwitches(payload.getData()));
                        getAdapter().notifyDataSetChanged();
                    }
                    Snackbar.make(switchList, payload.getResponse(), Snackbar.LENGTH_SHORT).show();
                }
                else {
                    messageHistory.add(payload.getResponse());
                    getAdapter().notifyDataSetChanged();
                }

                break;
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
            if (isDeleting || recyclerView.getAdapter() instanceof ChatAdapter) return 0;
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
            if (nsdConnection.isBound() && !deletedItems.isEmpty()) {
                nsdConnection.getBoundService().sendMessage(
                        Payload.builder().setAction(getString(R.string.blercprotocol_delete_command))
                                .setData(pop().serialize())
                                .build()
                );
            }
            isDeleting = false;
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
