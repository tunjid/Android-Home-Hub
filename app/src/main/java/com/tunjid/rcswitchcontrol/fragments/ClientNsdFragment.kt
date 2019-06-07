package com.tunjid.rcswitchcontrol.fragments

import android.content.Intent
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.Base64
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.Callback.makeMovementFlags
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
import com.tunjid.androidbootstrap.recyclerview.ListManager
import com.tunjid.androidbootstrap.recyclerview.ListManagerBuilder
import com.tunjid.androidbootstrap.recyclerview.ListPlaceholder
import com.tunjid.androidbootstrap.recyclerview.SwipeDragOptionsBuilder
import com.tunjid.androidbootstrap.view.util.ViewUtil
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.activities.MainActivity
import com.tunjid.rcswitchcontrol.adapters.ChatAdapter
import com.tunjid.rcswitchcontrol.adapters.RemoteSwitchAdapter
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.tunjid.rcswitchcontrol.dialogfragments.RenameSwitchDialogFragment
import com.tunjid.rcswitchcontrol.model.Payload
import com.tunjid.rcswitchcontrol.model.RcSwitch
import com.tunjid.rcswitchcontrol.services.ClientBleService
import com.tunjid.rcswitchcontrol.services.ClientNsdService
import com.tunjid.rcswitchcontrol.utils.DeletionHandler
import com.tunjid.rcswitchcontrol.utils.SpanCountCalculator
import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel
import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel.State
import java.util.Objects.requireNonNull

class ClientNsdFragment : BaseFragment(), ChatAdapter.ChatAdapterListener, RemoteSwitchAdapter.SwitchListener, RenameSwitchDialogFragment.SwitchNameListener {

    private var isDeleting: Boolean = false

    private lateinit var connectionStatus: TextView
    private lateinit var mainList: RecyclerView
    private lateinit var commandsView: RecyclerView
    private lateinit var listManager: ListManager<RecyclerView.ViewHolder, ListPlaceholder<*>>

    private lateinit var viewModel: NsdClientViewModel

    private val swipeDirection: Int
        get() = if (isDeleting || listManager.recyclerView.adapter is ChatAdapter) 0
        else makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        viewModel = ViewModelProviders.of(this).get(NsdClientViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val root = inflater.inflate(R.layout.fragment_nsd_client, container, false)

        connectionStatus = root.findViewById(R.id.connection_status)
        mainList = root.findViewById(R.id.switch_list)
        commandsView = root.findViewById(R.id.commands)

        swapAdapter(false)
        Builder()
                .withRecyclerView(commandsView)
                .withAdapter(ChatAdapter(this, viewModel.commands))
                .build()

        return root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        toolBar.setTitle(R.string.switches)
    }

    override fun onResume() {
        super.onResume()
        disposables.add(viewModel.listen().subscribe(this::onPayloadReceived, Throwable::printStackTrace))
        disposables.add(viewModel.connectionState().subscribe(this::onConnectionStateChanged, Throwable::printStackTrace))
    }

    override fun onPause() {
        super.onPause()
        viewModel.onBackground()
    }

    override fun onDestroyView() {
//        mainList = null
//        connectionStatus = null
        super.onDestroyView()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_fragment_nsd_client, menu)
        menu.findItem(R.id.menu_connect).isVisible = viewModel.isConnected
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (!viewModel.isBound) return super.onOptionsItemSelected(item)

        when (item.itemId) {
            R.id.menu_connect -> {
                Broadcaster.push(Intent(ClientNsdService.ACTION_START_NSD_DISCOVERY))
                return true
            }
            R.id.menu_forget -> {
                viewModel.forgetService()

                startActivity(Intent(requireActivity(), MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                requireActivity().finish()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onTextClicked(text: String) {
        viewModel.sendMessage(Payload.builder().setAction(text).build())
    }

    override fun onLongClicked(rcSwitch: RcSwitch) {
        RenameSwitchDialogFragment.newInstance(rcSwitch).show(childFragmentManager, "")
    }

    override fun onSwitchToggled(rcSwitch: RcSwitch, state: Boolean) {
        viewModel.sendMessage(Payload.builder().setAction(ClientBleService.ACTION_TRANSMITTER)
                .setData(Base64.encodeToString(rcSwitch.getTransmission(state), Base64.DEFAULT))
                .build())
    }

    override fun onSwitchRenamed(rcSwitch: RcSwitch) {
        listManager.notifyItemChanged(viewModel.switches.indexOf(rcSwitch))
        viewModel.sendMessage(Payload.builder().setAction(getString(R.string.blercprotocol_rename_command))
                .setData(rcSwitch.serialize())
                .build())
    }

    private fun onPayloadReceived(state: State) {
        TransitionManager.beginDelayedTransition(mainList.parent as ViewGroup, AutoTransition()
                .excludeTarget(mainList, true)
                .excludeTarget(commandsView, true))

        requireNonNull<RecyclerView.Adapter<*>>(commandsView.adapter).notifyDataSetChanged()
        ViewUtil.listenForLayout(commandsView) { ViewUtil.getLayoutParams(mainList).bottomMargin = commandsView.height }

        swapAdapter(state.isRc)
        listManager.onDiff(state.result)

        if (state.prompt != null)
            Snackbar.make(mainList, state.prompt, LENGTH_SHORT).show()
        else
            listManager.post {
                listManager.recyclerView
                        .smoothScrollToPosition(viewModel.latestHistoryIndex)
            }
    }

    private fun onConnectionStateChanged(text: String) {
        requireActivity().invalidateOptionsMenu()
        connectionStatus.text = resources.getString(R.string.connection_state, text)
    }

    private fun swapAdapter(isSwitchAdapter: Boolean) {
        val currentAdapter = mainList.adapter

        if (isSwitchAdapter && currentAdapter is RemoteSwitchAdapter) return
        if (!isSwitchAdapter && currentAdapter is ChatAdapter) return

        listManager = ListManagerBuilder<RecyclerView.ViewHolder, ListPlaceholder<*>>()
                .withRecyclerView(mainList)
                .withGridLayoutManager(SpanCountCalculator.spanCount)
                .withAdapter(if (isSwitchAdapter)
                    RemoteSwitchAdapter(this, viewModel.switches)
                else
                    ChatAdapter(object : ChatAdapter.ChatAdapterListener {
                        override fun onTextClicked(text: String) {}
                    }, viewModel.history))
                .withSwipeDragOptions(SwipeDragOptionsBuilder<RecyclerView.ViewHolder>()
                        .setSwipeConsumer { viewHolder, _ -> onDelete(viewHolder) }
                        .setMovementFlagsFunction { swipeDirection }
                        .setItemViewSwipeSupplier { true }
                        .build())
                .withInconsistencyHandler { requireActivity().recreate() }
                .build()
    }

    private fun onDelete(viewHolder: RecyclerView.ViewHolder) {
        if (isDeleting) return
        isDeleting = true

        if (view == null) return

        val position = viewHolder.adapterPosition

        val switches = viewModel.switches
        val deletionHandler = DeletionHandler<RcSwitch>(position) { self ->
            if (self.hasItems())
                viewModel.sendMessage(Payload.builder()
                        .setAction(getString(R.string.blercprotocol_delete_command))
                        .setData(self.pop().serialize())
                        .build())

            isDeleting = false
        }

        deletionHandler.push(switches[position])
        switches.removeAt(position)
        listManager.notifyItemRemoved(position)

        showSnackBar { snackBar ->
            snackBar.setText(R.string.deleted_switch)
                    .addCallback(deletionHandler)
                    .setAction(R.string.undo) {
                        if (deletionHandler.hasItems()) {
                            val deletedAt = deletionHandler.deletedPosition
                            switches.add(deletedAt, deletionHandler.pop())
                            listManager.notifyItemInserted(deletedAt)
                        }
                        isDeleting = false
                    }
        }
    }

    internal class Builder : ListManagerBuilder<ChatAdapter.TextViewHolder, ListPlaceholder<*>>() {

        override fun buildLayoutManager(): RecyclerView.LayoutManager {
            val layoutManager = FlexboxLayoutManager(recyclerView.context)
            layoutManager.alignItems = AlignItems.CENTER
            layoutManager.flexDirection = FlexDirection.ROW
            layoutManager.justifyContent = JustifyContent.FLEX_START

            return layoutManager
        }
    }

    companion object {

        fun newInstance(): ClientNsdFragment {
            val fragment = ClientNsdFragment()
            val bundle = Bundle()

            fragment.arguments = bundle
            return fragment
        }
    }
}
