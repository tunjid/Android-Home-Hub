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
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.fragment.app.activityViewModels
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.tunjid.androidx.core.components.args
import com.tunjid.androidx.recyclerview.ListManager
import com.tunjid.androidx.recyclerview.ListManagerBuilder
import com.tunjid.androidx.recyclerview.ListPlaceholder
import com.tunjid.androidx.recyclerview.adapterOf
import com.tunjid.androidx.view.util.inflate
import com.tunjid.androidx.view.util.spring
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.data.Record
import com.tunjid.rcswitchcontrol.utils.WindowInsetsDriver.Companion.bottomInset
import com.tunjid.rcswitchcontrol.utils.guard
import com.tunjid.rcswitchcontrol.viewholders.RecordViewHolder
import com.tunjid.rcswitchcontrol.viewmodels.ControlViewModel
import com.tunjid.rcswitchcontrol.viewmodels.ControlViewModel.State
import com.tunjid.rcswitchcontrol.viewmodels.ProtocolKey

sealed class RecordFragment : BaseFragment(R.layout.fragment_list) {

    class HistoryFragment : RecordFragment()

    class CommandsFragment : RecordFragment()

    internal var key: String? by args()
    internal var inTv: Boolean? by args()
    private val viewModel by activityViewModels<ControlViewModel>()

    private lateinit var listManager: ListManager<RecordViewHolder, ListPlaceholder<*>>

    override val stableTag: String get() = "${javaClass.simpleName}-$key"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = view.run {
        super.onViewCreated(view, savedInstanceState)
        updatePadding(bottom = bottomInset)

        val builder = ListManagerBuilder<RecordViewHolder, ListPlaceholder<*>>()
                .withRecyclerView(findViewById(R.id.list))
                .withAdapter(adapterOf(
                        itemsSource = { viewModel.getCommands(key) },
                        viewHolderCreator = { parent, _ ->
                            RecordViewHolder(
                                    parent.inflate(if (key == null) R.layout.viewholder_history else R.layout.viewholder_command),
                                    if (key == null) null else this@RecordFragment::onRecordClicked
                            ).apply { configureViewHolder(this) }
                        },
                        viewHolderBinder = { holder, record, _ -> holder.bind(record) }
                ))
                .withInconsistencyHandler(this@RecordFragment::onInconsistentList)

        if (key == null) builder.withLinearLayoutManager()
        else builder.withCustomLayoutManager(FlexboxLayoutManager(context).apply {
            alignItems = AlignItems.CENTER
            flexDirection = FlexDirection.ROW
            justifyContent = JustifyContent.CENTER
        })

        listManager = builder.build()
    }

    override fun onResume() {
        super.onResume()
        updateUi(altToolBarShows = false)
    }

    override fun onStart() {
        super.onStart()
        when (key) {
            null -> viewModel.listen(State.History::class.java).subscribe(this::onHistoryStateReceived, Throwable::printStackTrace)
            else -> viewModel.listen(State.Commands::class.java) { key == it.key }.subscribe(this::onCommandStateReceived, Throwable::printStackTrace)
        }.guard(lifecycleDisposable)
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

    private fun configureViewHolder(viewHolder: RecordViewHolder) = viewHolder.textView.run {
        if (inTv.let { it != null && it }) return@run

        isFocusable = true
        isFocusableInTouchMode = true
        textSize = context.resources.getDimensionPixelSize(R.dimen.regular_text).toFloat()
        setOnFocusChangeListener { _, hasFocus ->
            spring(SpringAnimation.SCALE_Y).animateToFinalPosition(if (hasFocus) 1.1F else 1F)
            spring(SpringAnimation.SCALE_X).animateToFinalPosition(if (hasFocus) 1.1F else 1F)

            strokeWidth =
                    if (hasFocus) context.resources.getDimensionPixelSize(R.dimen.quarter_margin)
                    else 0
        }
    }

    private fun onCommandStateReceived(state: State.Commands) = listManager.onDiff(state.result)

    companion object {

        fun historyInstance(): HistoryFragment = HistoryFragment().apply { this.inTv = false }

        fun commandInstance(key: ProtocolKey): CommandsFragment = CommandsFragment().apply { this.key = key.name; this.inTv = false }

        fun tvCommandInstance(key: ProtocolKey): CommandsFragment = CommandsFragment().apply { this.key = key.name; this.inTv = true }
    }
}
