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

package com.tunjid.rcswitchcontrol.control

import android.os.Bundle
import android.view.View
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.rcswitchcontrol.protocols.CommsProtocol
import com.tunjid.androidx.core.delegates.fragmentArgs
import com.tunjid.androidx.recyclerview.listAdapterOf
import com.tunjid.androidx.recyclerview.verticalLayoutManager
import com.tunjid.androidx.recyclerview.viewbinding.typed
import com.tunjid.globalui.liveUiState
import com.tunjid.globalui.uiState
import com.tunjid.globalui.updatePartial
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.common.mapDistinct
import com.tunjid.rcswitchcontrol.databinding.FragmentListBinding
import com.tunjid.rcswitchcontrol.databinding.ViewholderCommandBinding
import com.tunjid.rcswitchcontrol.databinding.ViewholderHistoryBinding
import com.tunjid.rcswitchcontrol.di.activityViewModelFactory
import com.tunjid.rcswitchcontrol.viewholders.bind
import com.tunjid.rcswitchcontrol.viewholders.bindCommand
import com.tunjid.rcswitchcontrol.viewholders.commandViewHolder
import com.tunjid.rcswitchcontrol.viewholders.historyViewHolder

sealed class RecordFragment : Fragment(R.layout.fragment_list) {

    class HistoryFragment : RecordFragment()
    class CommandsFragment : RecordFragment()

    internal var key: CommsProtocol.Key? by fragmentArgs()
    private val viewModel by activityViewModelFactory<ControlViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        liveUiState.mapDistinct { it.systemUI.dynamic.bottomInset }.observe(viewLifecycleOwner) {
            view.updatePadding(bottom = it)
        }

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
        uiState
        ::uiState.updatePartial { copy(altToolbarShows = false) }
    }

    private fun onRecordClicked(record: Record) = when (record) {
        is Record.Command -> viewModel.dispatchPayload(record.payload)
        is Record.Response -> Unit
    }

    companion object {

        fun historyInstance(): HistoryFragment = HistoryFragment()

        fun commandInstance(key: ProtocolKey): CommandsFragment = CommandsFragment().apply { this.key = key.key }
    }
}
