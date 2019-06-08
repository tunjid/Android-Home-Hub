package com.tunjid.rcswitchcontrol.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.Callback.makeMovementFlags
import androidx.recyclerview.widget.RecyclerView
import com.tunjid.androidbootstrap.recyclerview.ListManager
import com.tunjid.androidbootstrap.recyclerview.ListManagerBuilder
import com.tunjid.androidbootstrap.recyclerview.ListPlaceholder
import com.tunjid.androidbootstrap.recyclerview.SwipeDragOptionsBuilder
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.adapters.ChatAdapter
import com.tunjid.rcswitchcontrol.adapters.RemoteSwitchAdapter
import com.tunjid.rcswitchcontrol.dialogfragments.RenameSwitchDialogFragment
import com.tunjid.rcswitchcontrol.model.Payload
import com.tunjid.rcswitchcontrol.model.RcSwitch
import com.tunjid.rcswitchcontrol.services.ClientBleService
import com.tunjid.rcswitchcontrol.utils.DeletionHandler
import com.tunjid.rcswitchcontrol.utils.SpanCountCalculator
import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel

class NsdSwitchFragment : BaseFragment(), RemoteSwitchAdapter.SwitchListener, RenameSwitchDialogFragment.SwitchNameListener {

    private var isDeleting: Boolean = false

    private lateinit var viewModel: NsdClientViewModel
    private lateinit var listManager: ListManager<RecyclerView.ViewHolder, ListPlaceholder<*>>


    private val swipeDirection: Int
        get() = if (isDeleting || listManager.recyclerView.adapter is ChatAdapter) 0
        else makeMovementFlags(0, ItemTouchHelper.LEFT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(parentFragment!!).get(NsdClientViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val root = inflater.inflate(R.layout.fragment_list, container, false)
        listManager = ListManagerBuilder<RecyclerView.ViewHolder, ListPlaceholder<*>>()
                .withRecyclerView(root.findViewById(R.id.list))
                .withGridLayoutManager(SpanCountCalculator.spanCount)
                .withAdapter(RemoteSwitchAdapter(this, viewModel.switches))
                .withSwipeDragOptions(SwipeDragOptionsBuilder<RecyclerView.ViewHolder>()
                        .setSwipeConsumer { viewHolder, _ -> onDelete(viewHolder) }
                        .setMovementFlagsFunction { swipeDirection }
                        .setItemViewSwipeSupplier { true }
                        .build())
                .withInconsistencyHandler { requireActivity().recreate() }
                .build()

        return root
    }

    override fun onResume() {
        super.onResume()
        disposables.add(viewModel.listen { it.isRc }.subscribe({ listManager.onDiff(it.result) }, Throwable::printStackTrace))
    }

    override fun onDestroyView() {
        listManager.clear()
        super.onDestroyView()
    }

    override fun onLongClicked(rcSwitch: RcSwitch) =
            RenameSwitchDialogFragment.newInstance(rcSwitch).show(childFragmentManager, "")

    override fun onSwitchToggled(rcSwitch: RcSwitch, state: Boolean) =
            viewModel.sendMessage(Payload.builder().setAction(ClientBleService.ACTION_TRANSMITTER)
                    .setData(rcSwitch.getEncodedTransmission(state))
                    .build())

    override fun onSwitchRenamed(rcSwitch: RcSwitch) {
        listManager.notifyItemChanged(viewModel.switches.indexOf(rcSwitch))
        viewModel.sendMessage(Payload.builder().setAction(getString(R.string.blercprotocol_rename_command))
                .setData(rcSwitch.serialize())
                .build())
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

    companion object {

        fun newInstance(): NsdSwitchFragment {
            val fragment = NsdSwitchFragment()
            val bundle = Bundle()

            fragment.arguments = bundle
            return fragment
        }
    }
}
