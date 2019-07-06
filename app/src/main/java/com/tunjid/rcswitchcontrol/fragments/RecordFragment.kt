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
import com.tunjid.rcswitchcontrol.adapters.RecordAdapter
import com.tunjid.rcswitchcontrol.data.Record
import com.tunjid.rcswitchcontrol.utils.SpanCountCalculator
import com.tunjid.rcswitchcontrol.viewmodels.ControlViewModel
import com.tunjid.rcswitchcontrol.viewmodels.ControlViewModel.State

sealed class RecordFragment : BaseFragment() {

    class HistoryFragment(): RecordFragment()

    class CommandsFragment(): RecordFragment()


    private lateinit var listManager: ListManager<RecyclerView.ViewHolder, ListPlaceholder<*>>

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
        val builder = ListManagerBuilder<RecyclerView.ViewHolder, ListPlaceholder<*>>()
                .withRecyclerView(root.findViewById(R.id.list))
                .withAdapter(RecordAdapter(viewModel.getCommands(key), object : RecordAdapter.ChatAdapterListener {
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

    private fun onCommandStateReceived(state: State.Commands) = listManager.onDiff(state.result)

    companion object {

        const val KEY = "KEY"

        fun historyInstance(): HistoryFragment = HistoryFragment().apply { arguments = Bundle() }

        fun commandInstance(key: String): CommandsFragment = CommandsFragment().apply { arguments = Bundle().apply { putString(KEY, key) } }
    }
}
