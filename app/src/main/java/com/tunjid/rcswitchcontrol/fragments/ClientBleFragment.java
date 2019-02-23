package com.tunjid.rcswitchcontrol.fragments;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.snackbar.Snackbar;
import com.tunjid.androidbootstrap.material.animator.FabExtensionAnimator;
import com.tunjid.androidbootstrap.recyclerview.ListManager;
import com.tunjid.androidbootstrap.recyclerview.ListManagerBuilder;
import com.tunjid.androidbootstrap.recyclerview.ListPlaceholder;
import com.tunjid.androidbootstrap.recyclerview.SwipeDragOptionsBuilder;
import com.tunjid.androidbootstrap.view.animator.ViewHider;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment;
import com.tunjid.rcswitchcontrol.activities.MainActivity;
import com.tunjid.rcswitchcontrol.adapters.RemoteSwitchAdapter;
import com.tunjid.rcswitchcontrol.dialogfragments.NameServiceDialogFragment;
import com.tunjid.rcswitchcontrol.dialogfragments.RenameSwitchDialogFragment;
import com.tunjid.rcswitchcontrol.model.RcSwitch;
import com.tunjid.rcswitchcontrol.services.ClientBleService;
import com.tunjid.rcswitchcontrol.services.ServerNsdService;
import com.tunjid.rcswitchcontrol.utils.DeletionHandler;
import com.tunjid.rcswitchcontrol.viewmodels.BleClientViewModel;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import static com.tunjid.rcswitchcontrol.App.isServiceRunning;
import static com.tunjid.rcswitchcontrol.services.ClientBleService.ACTION_CONTROL;
import static com.tunjid.rcswitchcontrol.services.ClientBleService.ACTION_SNIFFER;
import static com.tunjid.rcswitchcontrol.services.ClientBleService.BLUETOOTH_DEVICE;
import static java.util.Objects.requireNonNull;

public class ClientBleFragment extends BaseFragment
        implements
        RemoteSwitchAdapter.SwitchListener,
        RenameSwitchDialogFragment.SwitchNameListener,
        NameServiceDialogFragment.ServiceNameListener {

    private int lastOffSet;
    private boolean isDeleting;

    private View progressBar;
    private TextView connectionStatus;
    private ListManager<RemoteSwitchAdapter.ViewHolder, ListPlaceholder> listManager;

    private ViewHider viewHider;
    private BleClientViewModel viewModel;

    public static ClientBleFragment newInstance(BluetoothDevice bluetoothDevice) {
        ClientBleFragment fragment = new ClientBleFragment();
        Bundle args = new Bundle();
        args.putParcelable(BLUETOOTH_DEVICE, bluetoothDevice);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        viewModel = ViewModelProviders.of(requireActivity()).get(BleClientViewModel.class);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_ble_client, container, false);
        AppBarLayout appBarLayout = root.findViewById(R.id.app_bar_layout);

        progressBar = root.findViewById(R.id.progress_bar);
        connectionStatus = root.findViewById(R.id.connection_status);

        listManager = new ListManagerBuilder<RemoteSwitchAdapter.ViewHolder, ListPlaceholder>()
                .withRecyclerView(root.findViewById(R.id.switch_list))
                .withAdapter(new RemoteSwitchAdapter(this, viewModel.getSwitches()))
                .withLinearLayoutManager()
                .addScrollListener((dx, dy) -> {
                    if (dy == 0) return;
                    if (dy > 0) viewHider.hide();
                    else viewHider.show();
                })
                .withSwipeDragOptions(new SwipeDragOptionsBuilder<RemoteSwitchAdapter.ViewHolder>()
                        .setMovementFlagsFunction(holder -> getSwipeDirection())
                        .setSwipeConsumer(((viewHolder, direction) -> onDelete(viewHolder)))
                        .build())
                .build();

        viewHider = ViewHider.of(root.findViewById(R.id.button_container))
                .setDuration(ViewHider.BOTTOM).build();

        appBarLayout.addOnOffsetChangedListener((appBarLayout1, verticalOffset) -> {
            if (verticalOffset == 0) return;
            if (verticalOffset > lastOffSet) viewHider.hide();
            else viewHider.show();

            lastOffSet = verticalOffset;
        });

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        getToolBar().setTitle(R.string.switches);

        BluetoothDevice device = requireNonNull(getArguments()).getParcelable(BLUETOOTH_DEVICE);
        disposables.add(viewModel.listenBle(device).subscribe(this::onReceive, Throwable::printStackTrace));
        disposables.add(viewModel.connectionState().subscribe(this::onConnectionStateChanged, Throwable::printStackTrace));
        disposables.add(viewModel.listenServer().subscribe(connected -> requireActivity().invalidateOptionsMenu(), Throwable::printStackTrace));
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_ble_client, menu);

        menu.findItem(R.id.menu_start_nsd).setVisible(!isServiceRunning(ServerNsdService.class));
        menu.findItem(R.id.menu_restart_nsd).setVisible(isServiceRunning(ServerNsdService.class));

        if (viewModel.isBleBound()) {
            menu.findItem(R.id.menu_connect).setVisible(!viewModel.isBleConnected());
            menu.findItem(R.id.menu_disconnect).setVisible(viewModel.isBleConnected());
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!viewModel.isBleBound()) return super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
            case R.id.menu_connect:
                viewModel.reconnectBluetooth();
                return true;
            case R.id.menu_disconnect:
                viewModel.disconnectBluetooth();
                return true;
            case R.id.menu_start_nsd:
                NameServiceDialogFragment.newInstance().show(getChildFragmentManager(), "");
                break;
            case R.id.menu_restart_nsd:
                viewModel.restartServer();
                break;
            case R.id.menu_forget:
                viewModel.forgetBluetoothDevice();

                Activity activity = requireActivity();
                activity.finish();

                startActivity(new Intent(activity, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroyView() {
        listManager.clear();
        listManager = null;
        progressBar = null;
        connectionStatus = null;

        super.onDestroyView();
    }

    @Override protected boolean showsFab() {
        return true;
    }

    @Override protected FabExtensionAnimator.GlyphState getFabState() {
        return viewModel.getFabState();
    }

    @Override protected View.OnClickListener getFabClickListener() {
        return view -> {
            toggleProgress(true);
            viewModel.sniffRcSwitch();
        };
    }

    @Override
    public void onLongClicked(RcSwitch rcSwitch) {
        RenameSwitchDialogFragment.newInstance(rcSwitch).show(getChildFragmentManager(), "");
    }

    @Override
    public void onSwitchToggled(RcSwitch rcSwitch, boolean state) {
        viewModel.toggleSwitch(rcSwitch, state);
    }

    @Override
    public void onSwitchRenamed(RcSwitch rcSwitch) {
        listManager.notifyItemChanged(viewModel.onSwitchUpdated(rcSwitch));
    }

    @Override
    public void onServiceNamed(String name) {
        viewModel.nameServer(name);
    }

    private void onConnectionStateChanged(String status) {
        requireActivity().invalidateOptionsMenu();
        connectionStatus.setText(status);
    }

    private void toggleProgress(boolean show) {
        TransitionManager.beginDelayedTransition((ViewGroup) progressBar.getParent(), new AutoTransition());
        progressBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        togglePersistentUi();
    }

    private void onReceive(Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case ACTION_CONTROL:
                byte[] rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_CONTROL);
                toggleProgress(rawData[0] == 0);
                break;
            case ACTION_SNIFFER: {
                listManager.notifyDataSetChanged();
                toggleProgress(false);
                break;
            }
        }
    }

    private int getSwipeDirection() {
        return isDeleting ? 0 : ItemTouchHelper.SimpleCallback.makeMovementFlags(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
    }

    private void onDelete(RecyclerView.ViewHolder viewHolder) {
        if (isDeleting) return;
        isDeleting = true;

        View root = getView();

        if (root == null) return;
        int position = viewHolder.getAdapterPosition();
        List<RcSwitch> switches = viewModel.getSwitches();

        DeletionHandler<RcSwitch> deletionHandler = new DeletionHandler<>(position, () -> {
            isDeleting = false;
            RcSwitch.saveSwitches(switches);
        });

        deletionHandler.push(switches.get(position));

        Snackbar.make(root, R.string.deleted_switch, Snackbar.LENGTH_LONG)
                .addCallback(deletionHandler)
                .setAction(R.string.undo, view -> {
                    if (!deletionHandler.hasItems()) return;

                    int deletedAt = deletionHandler.getDeletedPosition();
                    switches.add(deletedAt, deletionHandler.pop());
                    listManager.notifyItemInserted(deletedAt);

                    isDeleting = false;
                })
                .show();
    }
}
