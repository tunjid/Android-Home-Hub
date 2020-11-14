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
import android.view.View
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.tunjid.androidx.core.components.args
import com.tunjid.androidx.recyclerview.listAdapterOf
import com.tunjid.androidx.recyclerview.verticalLayoutManager
import com.tunjid.androidx.recyclerview.viewbinding.typed
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.common.mapDistinct
import com.tunjid.rcswitchcontrol.databinding.FragmentListBinding
import com.tunjid.rcswitchcontrol.databinding.ViewholderCommandBinding
import com.tunjid.rcswitchcontrol.databinding.ViewholderHistoryBinding
import com.tunjid.rcswitchcontrol.models.ControlState
import com.tunjid.rcswitchcontrol.models.ProtocolKey
import com.tunjid.rcswitchcontrol.models.Record
import com.tunjid.rcswitchcontrol.models.payload
import com.tunjid.rcswitchcontrol.utils.WindowInsetsDriver.Companion.bottomInset
import com.tunjid.rcswitchcontrol.viewholders.bind
import com.tunjid.rcswitchcontrol.viewholders.bindCommand
import com.tunjid.rcswitchcontrol.viewholders.commandViewHolder
import com.tunjid.rcswitchcontrol.viewholders.historyViewHolder
import com.tunjid.rcswitchcontrol.viewmodels.ControlViewModel

sealed class RecordFragment : BaseFragment(R.layout.fragment_list) {

    class HistoryFragment : RecordFragment()
    class CommandsFragment : RecordFragment()

    internal var key: String? by args()
    private val viewModel by activityViewModels<ControlViewModel>()

    override val stableTag: String get() = "${javaClass.simpleName}-$key"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.updatePadding(bottom = bottomInset)

        val binding = FragmentListBinding.bind(view)
        binding.list.apply {
            val listAdapter = listAdapterOf(
                    initialItems = initialItems(),
                    viewHolderCreator = { parent, viewType ->
                        when (viewType) {
                            0 -> parent.historyViewHolder()
                            1 -> parent.commandViewHolder(::onRecordClicked)
                            else -> throw IllegalArgumentException("Invalid view type")
                        }
                    },
                    viewHolderBinder = { holder, record, _ ->
                        when (holder.binding) {
                            is ViewholderHistoryBinding -> holder.typed<ViewholderHistoryBinding>().bind(record)
                            is ViewholderCommandBinding -> holder.typed<ViewholderCommandBinding>().bindCommand(record)
                        }
                    },
                    viewTypeFunction = { if (key == null) 0 else 1 }
            )

            layoutManager = when (key) {
                null -> verticalLayoutManager()
                else -> FlexboxLayoutManager(context).apply {
                    alignItems = AlignItems.CENTER
                    flexDirection = FlexDirection.ROW
                    justifyContent = JustifyContent.CENTER
                }
            }
            adapter = listAdapter

            viewModel.state.apply {
                if (key == null) mapDistinct(ControlState::history).observe(viewLifecycleOwner) { history ->
                    listAdapter.submitList(history)
                    if (history.isNotEmpty()) smoothScrollToPosition(history.lastIndex)
                }
                else mapDistinct { it.commands[key] }.observe(viewLifecycleOwner) {
                    it?.let(listAdapter::submitList)
                }
            }
        }
    }

    private fun initialItems(): List<Record> = viewModel.state.value?.let {
        if (key == null) it.history else it.commands[key]
    } ?: listOf()

    override fun onResume() {
        super.onResume()
        updateUi(altToolBarShows = false)
    }

    private fun onRecordClicked(record: Record) = viewModel.dispatchPayload(record.payload)

    companion object {

        fun historyInstance(): HistoryFragment = HistoryFragment()

        fun commandInstance(key: ProtocolKey): CommandsFragment = CommandsFragment().apply { this.key = key.name }
    }
}
