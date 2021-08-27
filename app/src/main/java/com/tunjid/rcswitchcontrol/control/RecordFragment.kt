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

import android.view.ViewGroup
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.rcswitchcontrol.protocols.CommsProtocol
import com.tunjid.androidx.recyclerview.listAdapterOf
import com.tunjid.androidx.recyclerview.verticalLayoutManager
import com.tunjid.androidx.recyclerview.viewbinding.typed
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderFrom
import com.tunjid.globalui.liveUiState
import com.tunjid.rcswitchcontrol.client.ClientState
import com.tunjid.rcswitchcontrol.common.asSuspend
import com.tunjid.rcswitchcontrol.common.mapDistinct
import com.tunjid.rcswitchcontrol.databinding.FragmentListBinding
import com.tunjid.rcswitchcontrol.databinding.ViewholderCommandBinding
import com.tunjid.rcswitchcontrol.databinding.ViewholderHistoryBinding
import com.tunjid.rcswitchcontrol.di.dagger
import com.tunjid.rcswitchcontrol.di.stateMachine
import com.tunjid.rcswitchcontrol.navigation.Node
import com.tunjid.rcswitchcontrol.viewholders.bind
import com.tunjid.rcswitchcontrol.viewholders.bindCommand
import com.tunjid.rcswitchcontrol.viewholders.commandViewHolder
import com.tunjid.rcswitchcontrol.viewholders.historyViewHolder
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

fun ViewGroup.recordScreen(
    node: Node,
    key: CommsProtocol.Key?
) = viewHolderFrom(FragmentListBinding::inflate).apply {

    val scope = binding.attachedScope()
    val dagger = binding.root.context.dagger
    val stateMachine = dagger.stateMachine<ControlViewModel>(node)

    val listAdapter = listAdapterOf(
        initialItems = stateMachine.state.value.clientState.let {
            if (key == null) it.history else it.commands[key]
        } ?: listOf(),
        viewHolderCreator = { parent, viewType ->
            when (viewType) {
                0 -> parent.historyViewHolder()
                1 -> parent.commandViewHolder { record ->
                    when (record) {
                        is Record.Command -> stateMachine.accept(Input.Async.ServerCommand(record.payload))
                        is Record.Response -> Unit
                    }
                }
                else -> throw IllegalArgumentException("Invalid view type")
            }
        },
        viewHolderBinder = { holder, record, _ ->
            when (holder.binding) {
                is ViewholderHistoryBinding -> holder.typed<ViewholderHistoryBinding>()
                    .bind(record)
                is ViewholderCommandBinding -> holder.typed<ViewholderCommandBinding>()
                    .bindCommand(record)
            }
        },
        viewTypeFunction = { if (key == null) 0 else 1 }
    )

    binding.list.apply {
        layoutManager = when (key) {
            null -> verticalLayoutManager()
            else -> FlexboxLayoutManager(context).apply {
                alignItems = AlignItems.CENTER
                flexDirection = FlexDirection.ROW
                justifyContent = JustifyContent.CENTER
            }
        }
        adapter = listAdapter
    }

    binding.root.doOnAttach {
        val clientState = stateMachine.state.mapDistinct(ControlState::clientState.asSuspend)

        scope.launch {
            liveUiState.mapDistinct { it.systemUI.dynamic.bottomInset }.collect {
                binding.root.updatePadding(bottom = it)
            }
        }
        scope.launch {
            if (key == null) clientState.mapDistinct(ClientState::history.asSuspend)
                .collect { history ->
                    listAdapter.submitList(history)
                    if (history.isNotEmpty()) binding.list.smoothScrollToPosition(history.lastIndex)
                }
            else clientState.mapDistinct { it.commands[key] }.collect {
                it?.let(listAdapter::submitList)
            }
        }

    }

//    override fun onResume() {
//        super.onResume()
//        uiState
//        ::uiState.updatePartial { copy(altToolbarShows = false) }
//    }
}
