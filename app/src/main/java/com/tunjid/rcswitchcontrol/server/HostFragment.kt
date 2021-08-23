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

package com.tunjid.rcswitchcontrol.server

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tunjid.androidx.recyclerview.gridLayoutManager
import com.tunjid.androidx.recyclerview.listAdapterOf
import com.tunjid.androidx.recyclerview.viewbinding.BindingViewHolder
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderDelegate
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderFrom
import com.tunjid.globalui.uiState
import com.tunjid.globalui.updatePartial
import com.tunjid.rcswitchcontrol.MainActivity
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.common.mapDistinct
import com.tunjid.rcswitchcontrol.control.NameServiceDialogFragment
import com.tunjid.rcswitchcontrol.databinding.FragmentListBinding
import com.tunjid.rcswitchcontrol.databinding.ViewholderHostCardBinding
import com.tunjid.rcswitchcontrol.di.rootStateMachine
import com.tunjid.rcswitchcontrol.utils.makeAccessibleForTV
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class HostFragment : Fragment(R.layout.fragment_list),
    NameServiceDialogFragment.ServiceNameListener {

    private val stateMachine by rootStateMachine<HostViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        FragmentListBinding.bind(view).list.apply {
            val listAdapter = listAdapterOf(
                initialItems = stateMachine.state.value.let(view.context::items),
                viewHolderCreator = { parent, _ ->
                    parent.viewHolderFrom(ViewholderHostCardBinding::inflate).apply {
                        binding.button.strokeColor = ColorStateList.valueOf(Color.WHITE)
                        binding.button.makeAccessibleForTV(stroked = true)
                        binding.root.setOnClickListener { onItemClicked(item) }
                    }
                },
                viewHolderBinder = { holder, item, _ ->
                    holder.item = item
                    holder.binding.button.text = item.text
                }
            )

            layoutManager = gridLayoutManager(2)
            adapter = listAdapter

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    stateMachine.state
                        .mapDistinct(context::items)
                        .collect(listAdapter::submitList)
                }
            }
        }
    }

    private fun onItemClicked(item: HostItem) = when (item.id) {
        R.string.rename_server -> NameServiceDialogFragment.newInstance().show(childFragmentManager, "")
        R.string.restart_server -> stateMachine.restartServer()
        R.string.stop_server -> {
            stateMachine.stop()
            startActivity(Intent(requireActivity(), MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            requireActivity().finish()
        }
        else -> Unit
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // Request permission for location to enable ble scanning
        requestPermissions(arrayOf(ACCESS_COARSE_LOCATION), 0)
    }

    override fun onResume() {
        super.onResume()
        ::uiState.updatePartial { copy(altToolbarShows = false) }
    }

    override fun onServiceNamed(name: String) = stateMachine.nameServer(name)

    companion object {
        fun newInstance(): HostFragment = HostFragment().apply { arguments = Bundle() }
    }
}

private var BindingViewHolder<ViewholderHostCardBinding>.item by viewHolderDelegate<HostItem>()