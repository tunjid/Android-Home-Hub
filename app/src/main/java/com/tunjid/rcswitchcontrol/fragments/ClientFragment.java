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
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment;
import com.tunjid.rcswitchcontrol.adapters.ChatAdapter;
import com.tunjid.rcswitchcontrol.nsd.nsdprotocols.Payload;
import com.tunjid.rcswitchcontrol.nsd.services.ClientNsdService;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 */
public class ClientFragment extends BaseFragment
        implements
        ServiceConnection,
        ChatAdapter.ChatAdapterListener {

    private boolean isReceiverRegistered;

    private NsdServiceInfo service;
    private ClientNsdService clientService;

    private ProgressDialog progressDialog;

    private RecyclerView historyView;
    private RecyclerView commandsView;

    private List<String> responses = new ArrayList<>();
    private List<String> commands = new ArrayList<>();

    private final IntentFilter clientServiceFilter = new IntentFilter();

    private final BroadcastReceiver clientServiceReceiever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ClientNsdService.ACTION_SOCKET_CONNECTED:
                    if (progressDialog != null) progressDialog.dismiss();
                    break;
                case ClientNsdService.ACTION_SERVER_RESPONSE:
                    String response = intent.getStringExtra(ClientNsdService.DATA_SERVER_RESPONSE);
                    Payload payload = Payload.deserialize(response);

                    commands.clear();
                    commands.addAll(payload.getCommands());

                    commandsView.getAdapter().notifyDataSetChanged();

                    responses.add(payload.getResponse());
                    historyView.getAdapter().notifyItemInserted(responses.size() - 1);
                    historyView.smoothScrollToPosition(responses.size() - 1);
                    break;
            }
        }
    };

    public ClientFragment() {
        // Required empty public constructor
    }

    public static ClientFragment newInstance(NsdServiceInfo nsdServiceInfo) {
        ClientFragment fragment = new ClientFragment();
        Bundle bundle = new Bundle();

        bundle.putParcelable(ClientNsdService.NSD_SERVICE_INFO_KEY, nsdServiceInfo);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        clientServiceFilter.addAction(ClientNsdService.ACTION_SOCKET_CONNECTED);
        clientServiceFilter.addAction(ClientNsdService.ACTION_SERVER_RESPONSE);

        service = getArguments().getParcelable(ClientNsdService.NSD_SERVICE_INFO_KEY);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        View rootView = inflater.inflate(R.layout.fragment_client, container, false);

        historyView = (RecyclerView) rootView.findViewById(R.id.list);
        commandsView = (RecyclerView) rootView.findViewById(R.id.commands);

        historyView.setAdapter(new ChatAdapter(null, responses));
        historyView.setLayoutManager(new LinearLayoutManager(getContext()));

        commandsView.setAdapter(new ChatAdapter(this, commands));
        commandsView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

//        floatingActionButton.hide();

        Intent clientIntent = new Intent(getActivity(), ClientNsdService.class);
        getActivity().bindService(clientIntent, this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        progressDialog = ProgressDialog.show(getActivity(),
                getString(R.string.connection_title),
                getString(R.string.connection_text), true, true);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        historyView = null;
        commandsView = null;
        progressDialog = null;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {

        clientService = ((ClientNsdService.NsdClientBinder) binder).getClientService();

        if (!isReceiverRegistered) {
            LocalBroadcastManager.getInstance(getContext()).registerReceiver(clientServiceReceiever, clientServiceFilter);
            isReceiverRegistered = true;
        }

        clientService.connect(service);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        clientService = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unbindService(this);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(clientServiceReceiever);
    }

    @Override
    public void onTextClicked(String text) {
        if (clientService != null) {
            clientService.sendMessage(text);

            responses.add(text);
            historyView.getAdapter().notifyItemInserted(responses.size() - 1);
        }
    }
}
