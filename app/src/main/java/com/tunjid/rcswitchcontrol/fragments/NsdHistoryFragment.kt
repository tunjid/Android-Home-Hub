package com.tunjid.rcswitchcontrol.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.RecyclerView
import com.tunjid.androidbootstrap.recyclerview.ListManager
import com.tunjid.androidbootstrap.recyclerview.ListManagerBuilder
import com.tunjid.androidbootstrap.recyclerview.ListPlaceholder
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.adapters.ChatAdapter
import com.tunjid.rcswitchcontrol.utils.SpanCountCalculator
import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel
import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel.State

class NsdHistoryFragment : BaseFragment(), ChatAdapter.ChatAdapterListener {

    private lateinit var listManager: ListManager<RecyclerView.ViewHolder, ListPlaceholder<*>>

    private lateinit var viewModel: NsdClientViewModel

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
                .withAdapter(ChatAdapter(viewModel.history, object : ChatAdapter.ChatAdapterListener {
                    override fun onTextClicked(text: String) {}
                }))
                .withInconsistencyHandler(this::onInconsistentList)
                .build()

        return root
    }

    override fun onResume() {
        super.onResume()
        disposables.add(viewModel.listen().subscribe(this::onPayloadReceived, Throwable::printStackTrace))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listManager.clear()
    }

    override fun onTextClicked(text: String) = viewModel.dispatchPayload { action = text }

    private fun onPayloadReceived(state: State) {
        listManager.onDiff(state.result)

        if (viewModel.history.isNotEmpty()) listManager.post {
            listManager.recyclerView?.smoothScrollToPosition(viewModel.latestHistoryIndex)
        }
    }

    companion object {

        fun newInstance(): NsdHistoryFragment {
            val fragment = NsdHistoryFragment()
            val bundle = Bundle()

            fragment.arguments = bundle
            return fragment
        }
    }
}
