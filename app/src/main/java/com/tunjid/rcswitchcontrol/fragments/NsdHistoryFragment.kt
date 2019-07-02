package com.tunjid.rcswitchcontrol.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.tunjid.androidbootstrap.recyclerview.ListManager
import com.tunjid.androidbootstrap.recyclerview.ListManagerBuilder
import com.tunjid.androidbootstrap.recyclerview.ListPlaceholder
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.adapters.ChatAdapter
import com.tunjid.rcswitchcontrol.data.Record
import com.tunjid.rcswitchcontrol.utils.SpanCountCalculator
import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel
import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel.State

class NsdHistoryFragment : BaseFragment() {

    private lateinit var listManager: ListManager<RecyclerView.ViewHolder, ListPlaceholder<*>>

    private lateinit var viewModel: NsdClientViewModel
    private var key: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(parentFragment!!).get(NsdClientViewModel::class.java)
        key = arguments?.getString(KEY)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val root = inflater.inflate(R.layout.fragment_list, container, false)
        val builder = ListManagerBuilder<RecyclerView.ViewHolder, ListPlaceholder<*>>()
                .withRecyclerView(root.findViewById(R.id.list))
                .withAdapter(ChatAdapter(viewModel.getCommands(key), object : ChatAdapter.ChatAdapterListener {
                    override fun onRecordClicked(record: Record) {
                        if (key != null) viewModel.dispatchPayload(record.key) { action = record.entry }
                    }
                }))
                .withInconsistencyHandler(this::onInconsistentList)

        if (key == null) builder.withGridLayoutManager(SpanCountCalculator.spanCount)
        else builder.withCustomLayoutManager(FlexboxLayoutManager(inflater.context).apply {
            alignItems = AlignItems.CENTER
            flexDirection = FlexDirection.ROW
            justifyContent = JustifyContent.FLEX_START
        })

        listManager = builder.build()

        return root
    }

    override fun onResume() {
        super.onResume()
        disposables.add(viewModel.listen(this::filter).subscribe(this::onPayloadReceived, Throwable::printStackTrace))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listManager.clear()
    }

    private fun filter(state: State): Boolean = if (key == null) state is State.History else key == state.key && state is State.Commands

    private fun onPayloadReceived(state: State) {
        listManager.onDiff(state.result)

        if (viewModel.getCommands(key).isNotEmpty()) listManager.post {
            listManager.recyclerView?.smoothScrollToPosition(viewModel.lastIndex(key))
        }
    }

    companion object {

        const val KEY = "KEY"

        fun newInstance(key: String? = null): NsdHistoryFragment {
            val fragment = NsdHistoryFragment()
            val bundle = Bundle().apply { putString(KEY, key) }

            fragment.arguments = bundle
            return fragment
        }
    }
}
