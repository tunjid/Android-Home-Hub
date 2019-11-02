/*
 * MIT License
 *
 * Copyright (c) 2019 Adetunji Dahunsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tunjid.rcswitchcontrol.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProviders
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.tunjid.androidx.recyclerview.ListManager
import com.tunjid.androidx.recyclerview.ListManagerBuilder
import com.tunjid.androidx.recyclerview.ListPlaceholder
import com.tunjid.androidx.recyclerview.adapterOf
import com.tunjid.androidx.view.util.inflate
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.viewholders.RecordViewHolder
import com.tunjid.rcswitchcontrol.viewholders.withPaddedAdapter
import com.tunjid.rcswitchcontrol.data.Record
import com.tunjid.rcswitchcontrol.viewmodels.ControlViewModel
import com.tunjid.rcswitchcontrol.viewmodels.ControlViewModel.State

sealed class RecordFragment : BaseFragment() {

    class HistoryFragment : RecordFragment()

    class CommandsFragment : RecordFragment()

    private lateinit var listManager: ListManager<RecordViewHolder, ListPlaceholder<*>>

    private lateinit var viewModel: ControlViewModel
    private var key: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(parentFragment!!).get(ControlViewModel::class.java)
        key = arguments?.getString(KEY)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {


        val root = inflater.inflate(R.layout.fragment_list, container, false)
        val builder = ListManagerBuilder<RecordViewHolder, ListPlaceholder<*>>()
                .withRecyclerView(root.findViewById(R.id.list))
                .withPaddedAdapter(
                        adapterOf(
                                itemsSource = { viewModel.getCommands(key) },
                                viewHolderCreator = { parent, _ ->
                                    RecordViewHolder(
                                            parent.inflate(if (key == null) R.layout.viewholder_history else R.layout.viewholder_command),
                                            if (key == null) null else this::onRecordClicked
                                    )
                                },
                                viewHolderBinder = { holder, record, _ -> holder.bind(record) }
                        )
                )
                .withInconsistencyHandler(this::onInconsistentList)

        if (key == null) builder.withLinearLayoutManager()
        else builder.withCustomLayoutManager(FlexboxLayoutManager(inflater.context).apply {
            alignItems = AlignItems.CENTER
            flexDirection = FlexDirection.ROW
            justifyContent = JustifyContent.CENTER
        })

        listManager = builder.build()

        return root
    }

    override fun onResume() {
        super.onResume()
        updateUi(altToolBarShows = false)
    }

    override fun onStart() {
        super.onStart()
        disposables.add(
                if (key == null) viewModel.listen(State.History::class.java).subscribe(this::onHistoryStateReceived, Throwable::printStackTrace)
                else viewModel.listen(State.Commands::class.java) { key == it.key }.subscribe(this::onCommandStateReceived, Throwable::printStackTrace)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listManager.clear()
    }

    private fun onHistoryStateReceived(state: State.History) {
        listManager.onDiff(state.result)

        if (viewModel.getCommands(key).isNotEmpty())
            listManager.post { listManager.recyclerView?.smoothScrollToPosition(viewModel.lastIndex(key)) }
    }

    private fun onRecordClicked(record: Record) =
            viewModel.dispatchPayload(record.key) { action = record.entry }

    private fun onCommandStateReceived(state: State.Commands) = listManager.onDiff(state.result)

    companion object {

        const val KEY = "KEY"

        fun historyInstance(): HistoryFragment = HistoryFragment().apply { arguments = Bundle() }

        fun commandInstance(key: String): CommandsFragment = CommandsFragment().apply { arguments = Bundle().apply { putString(KEY, key) } }
    }
}
