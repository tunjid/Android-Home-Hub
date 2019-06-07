package com.tunjid.rcswitchcontrol.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.Base64;
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
import com.tunjid.androidbootstrap.recyclerview.ListManager;
import com.tunjid.androidbootstrap.recyclerview.ListManagerBuilder;
import com.tunjid.androidbootstrap.recyclerview.ListPlaceholder;
import com.tunjid.androidbootstrap.recyclerview.SwipeDragOptionsBuilder;
import com.tunjid.androidbootstrap.view.util.ViewUtil;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment;
import com.tunjid.rcswitchcontrol.activities.MainActivity;
import com.tunjid.rcswitchcontrol.adapters.ChatAdapter;
import com.tunjid.rcswitchcontrol.adapters.RemoteSwitchAdapter;
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster;
import com.tunjid.rcswitchcontrol.dialogfragments.RenameSwitchDialogFragment;
import com.tunjid.rcswitchcontrol.model.Payload;
import com.tunjid.rcswitchcontrol.model.RcSwitch;
import com.tunjid.rcswitchcontrol.services.ClientBleService;
import com.tunjid.rcswitchcontrol.services.ClientNsdService;
import com.tunjid.rcswitchcontrol.utils.DeletionHandler;
import com.tunjid.rcswitchcontrol.utils.SpanCountCalculator;
import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import static androidx.recyclerview.widget.ItemTouchHelper.Callback.makeMovementFlags;
import static com.google.android.material.snackbar.Snackbar.LENGTH_SHORT;
import static com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel.State;
import static java.util.Objects.requireNonNull;

public class ClientNsdFragment extends BaseFragment
        implements
        ChatAdapter.ChatAdapterListener,
        RemoteSwitchAdapter.SwitchListener,
        RenameSwitchDialogFragment.SwitchNameListener {

    private boolean isDeleting;

    private TextView connectionStatus;
    private RecyclerView mainList;
    private RecyclerView commandsView;
    private ListManager<RecyclerView.ViewHolder, ListPlaceholder> listManager;

    private NsdClientViewModel viewModel;

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
        viewModel = ViewModelProviders.of(this).get(NsdClientViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_nsd_client, container, false);

        connectionStatus = root.findViewById(R.id.connection_status);
        mainList = root.findViewById(R.id.switch_list);
        commandsView = root.findViewById(R.id.commands);

        swapAdapter(false);
        new Builder()
                .withRecyclerView(commandsView)
                .withAdapter(new ChatAdapter(this, viewModel.getCommands()))
                .build();

        return root;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getToolBar().setTitle(R.string.switches);
    }

    @Override
    public void onResume() {
        super.onResume();
        getDisposables().add(viewModel.listen().subscribe(this::onPayloadReceived, Throwable::printStackTrace));
        getDisposables().add(viewModel.connectionState().subscribe(this::onConnectionStateChanged, Throwable::printStackTrace));
    }

    @Override
    public void onPause() {
        super.onPause();
        viewModel.onBackground();
    }

    @Override
    public void onDestroyView() {
        mainList = null;
        connectionStatus = null;
        super.onDestroyView();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_fragment_nsd_client, menu);
        menu.findItem(R.id.menu_connect).setVisible(viewModel.isConnected());
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!viewModel.isBound()) return super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
            case R.id.menu_connect:
                Broadcaster.push(new Intent(ClientNsdService.ACTION_START_NSD_DISCOVERY));
                return true;
            case R.id.menu_forget:
                viewModel.forgetService();

                startActivity(new Intent(requireActivity(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                requireActivity().finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTextClicked(String text) {
        viewModel.sendMessage(Payload.builder().setAction(text).build());
    }

    @Override
    public void onLongClicked(RcSwitch rcSwitch) {
        RenameSwitchDialogFragment.newInstance(rcSwitch).show(getChildFragmentManager(), "");
    }

    @Override
    public void onSwitchToggled(RcSwitch rcSwitch, boolean state) {
        viewModel.sendMessage(Payload.builder().setAction(ClientBleService.ACTION_TRANSMITTER)
                .setData(Base64.encodeToString(rcSwitch.getTransmission(state), Base64.DEFAULT))
                .build());
    }

    @Override
    public void onSwitchRenamed(RcSwitch rcSwitch) {
        listManager.notifyItemChanged(viewModel.getSwitches().indexOf(rcSwitch));
        viewModel.sendMessage(Payload.builder().setAction(getString(R.string.blercprotocol_rename_command))
                .setData(rcSwitch.serialize())
                .build());
    }

    private void onPayloadReceived(State state) {
        TransitionManager.beginDelayedTransition((ViewGroup) mainList.getParent(), new AutoTransition()
                .excludeTarget(mainList, true)
                .excludeTarget(commandsView, true));

        requireNonNull(commandsView.getAdapter()).notifyDataSetChanged();
        ViewUtil.listenForLayout(commandsView, () -> ViewUtil.getLayoutParams(mainList).bottomMargin = commandsView.getHeight());

        swapAdapter(state.isRc);
        listManager.onDiff(state.result);

        if (state.prompt != null) Snackbar.make(mainList, state.prompt, LENGTH_SHORT).show();
        else listManager.post(() -> listManager.getRecyclerView()
                .smoothScrollToPosition(viewModel.getLatestHistoryIndex()));
    }

    private void onConnectionStateChanged(String text) {
        requireActivity().invalidateOptionsMenu();
        connectionStatus.setText(getResources().getString(R.string.connection_state, text));
    }

    private void swapAdapter(boolean isSwitchAdapter) {
        RecyclerView.Adapter currentAdapter = mainList.getAdapter();

        if (isSwitchAdapter && currentAdapter instanceof RemoteSwitchAdapter) return;
        if (!isSwitchAdapter && currentAdapter instanceof ChatAdapter) return;

        listManager = new ListManagerBuilder<RecyclerView.ViewHolder, ListPlaceholder>()
                .withRecyclerView(mainList)
                .withGridLayoutManager(SpanCountCalculator.getSpanCount())
                .withAdapter(isSwitchAdapter
                        ? new RemoteSwitchAdapter(this, viewModel.getSwitches())
                        : new ChatAdapter(null, viewModel.getHistory()))
                .withSwipeDragOptions(new SwipeDragOptionsBuilder<>()
                        .setSwipeConsumer((viewHolder, direction) -> onDelete(viewHolder))
                        .setMovementFlagsFunction(holder -> getSwipeDirection())
                        .setItemViewSwipeSupplier(() -> true)
                        .build())
                .withInconsistencyHandler(__ -> requireActivity().recreate())
                .build();
    }

    private int getSwipeDirection() {
        return isDeleting || listManager.getRecyclerView().getAdapter() instanceof ChatAdapter ? 0 :
                makeMovementFlags(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
    }

    private void onDelete(RecyclerView.ViewHolder viewHolder) {
        if (isDeleting) return;
        isDeleting = true;

        View root = getView();

        if (root == null) return;
        int position = viewHolder.getAdapterPosition();

        List<RcSwitch> switches = viewModel.getSwitches();
        DeletionHandler<RcSwitch> deletionHandler = new DeletionHandler<>(position, self -> {
            if (self.hasItems()) viewModel.sendMessage(Payload.builder()
                    .setAction(getString(R.string.blercprotocol_delete_command))
                    .setData(self.pop().serialize())
                    .build());

            isDeleting = false;
        });

        deletionHandler.push(switches.get(position));
        switches.remove(position);
        listManager.notifyItemRemoved(position);

        showSnackBar(snackBar -> snackBar.setText(R.string.deleted_switch)
                .addCallback(deletionHandler)
                .setAction(R.string.undo, view -> {
                    if (deletionHandler.hasItems()) {
                        int deletedAt = deletionHandler.getDeletedPosition();
                        switches.add(deletedAt, deletionHandler.pop());
                        listManager.notifyItemInserted(deletedAt);
                    }
                    isDeleting = false;
                })
        );
    }

    static class Builder extends ListManagerBuilder<ChatAdapter.TextViewHolder, ListPlaceholder> {

        @Override
        protected RecyclerView.LayoutManager buildLayoutManager() {
            FlexboxLayoutManager layoutManager = new FlexboxLayoutManager(recyclerView.getContext());
            layoutManager.setAlignItems(AlignItems.CENTER);
            layoutManager.setFlexDirection(FlexDirection.ROW);
            layoutManager.setJustifyContent(JustifyContent.FLEX_START);

            return layoutManager;
        }
    }
}
