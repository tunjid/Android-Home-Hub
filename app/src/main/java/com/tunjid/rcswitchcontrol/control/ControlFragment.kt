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

import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnAttach
import androidx.core.view.doOnDetach
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.android.material.bottomsheet.setupForBottomSheet
import com.tunjid.androidx.core.content.colorAt
import com.tunjid.androidx.recyclerview.listAdapterOf
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderFrom
import com.tunjid.globalui.InsetFlags
import com.tunjid.globalui.UiState
import com.tunjid.globalui.uiState
import com.tunjid.globalui.updatePartial
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.client.ClientState
import com.tunjid.rcswitchcontrol.client.Status
import com.tunjid.rcswitchcontrol.client.isConnected
import com.tunjid.rcswitchcontrol.client.keys
import com.tunjid.rcswitchcontrol.common.asSuspend
import com.tunjid.rcswitchcontrol.common.mapDistinct
import com.tunjid.rcswitchcontrol.databinding.FragmentControlBinding
import com.tunjid.rcswitchcontrol.di.AppNav
import com.tunjid.rcswitchcontrol.di.dagger
import com.tunjid.rcswitchcontrol.di.isResumed
import com.tunjid.rcswitchcontrol.di.nav
import com.tunjid.rcswitchcontrol.di.stateMachine
import com.tunjid.rcswitchcontrol.models.Broadcast
import com.tunjid.rcswitchcontrol.navigation.Node
import com.tunjid.rcswitchcontrol.navigation.updatePartial
import com.tunjid.rcswitchcontrol.server.ServerNsdService
import com.tunjid.rcswitchcontrol.server.hostScreen
import com.tunjid.rcswitchcontrol.ui.root.AppNavContainer
import com.tunjid.rcswitchcontrol.utils.attach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

fun ViewBinding.attachedScope(): CoroutineScope {
    val scope = root.context.dagger.appComponent.uiScope()
    root.doOnAttach { root.doOnDetach { scope.cancel() } }
    return scope
}

fun <T> ViewBinding.whileAttached(action: (T) -> Unit): (T) -> Unit =
    WhileAttached(root, action = action)


class WhileAttached<T>(view: View, action: (T) -> Unit) : (T) -> Unit {
    private var backing = action

    init {
        view.doOnAttach {
            view.doOnDetach { backing = { } }
        }
    }

    override fun invoke(item: T) = backing(item)
}

fun ViewGroup.controlScreen(
    node: Node,
    load: ClientLoad,
) = viewHolderFrom(FragmentControlBinding::inflate).apply {
    val scope = binding.attachedScope()
    val dagger = binding.root.context.dagger
    val stateMachine = dagger.stateMachine<ControlViewModel>(node)

    binding.root.doOnAttach {
        stateMachine.accept(Input.Async.Load(load))

        val toolbarClick = binding.whileAttached { item: MenuItem ->
            if (stateMachine.isBound) when (item.itemId) {
                R.id.menu_ping -> stateMachine.accept(Input.Async.PingServer).let { true }
                R.id.menu_connect -> dagger.appComponent.broadcaster(Broadcast.ClientNsd.StartDiscovery())
                R.id.menu_forget -> {
                    stateMachine.accept(Input.Async.ForgetServer)
                    dagger::nav.updatePartial { AppNav() }
                }
                R.id.menu_rename_device -> {
//                        stateMachine.state.value
//                            .selectedDevices
//                            .firstOrNull()
//                            ?.editName
//                            ?.let(RenameSwitchDialogFragment.Companion::newInstance)
//                            ?.show(childFragmentManager, item.itemId.toString()).let { true }
                }
                R.id.menu_create_group -> {
//                        GroupDeviceDialogFragment.newInstance.show(
//                            childFragmentManager,
//                            item.itemId.toString()
//                        )
                }
                else -> Unit
            }
        }

        binding.uiState = UiState(
            toolbarShows = true,
            toolbarTitle = binding.root.context.getString(R.string.switches),
//            toolbarMenuRes = R.menu.menu_fragment_nsd_client,
//            toolbarMenuClickListener = toolbarClick,
//            altToolbarMenuRes = R.menu.menu_alt_devices,
//            altToolbarMenuClickListener = toolbarClick,
            navBarColor = binding.root.context.colorAt(R.color.black_50),
            insetFlags = InsetFlags.NO_BOTTOM
        )
    }

    val bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
    val offset =
        binding.root.context.resources.getDimensionPixelSize(R.dimen.triple_and_half_margin)

    val calculateTranslation: (slideOffset: Float) -> Float = calculate@{ slideOffset ->
        val peekHeight = bottomSheetBehavior.peekHeight.toFloat()
        when (slideOffset) {
            in -1F..0F -> peekHeight + (peekHeight * slideOffset)
            0F -> peekHeight
            else -> peekHeight + ((binding.bottomSheet.height - peekHeight) * slideOffset)
        }
    }

    val onPageSelected: (position: Int) -> Unit = {
        bottomSheetBehavior.state =
            if (stateMachine.pages[it] == Page.History) STATE_HALF_EXPANDED else STATE_HIDDEN
    }

    val pageAdapter = listAdapterOf(
        initialItems = stateMachine.pages,
        viewHolderCreator = { parent, ordinal ->
            when (Page.values()[ordinal]) {
                Page.Host -> parent.hostScreen(node = node)
                Page.History -> parent.recordScreen(node = node, key = null)
                Page.Devices -> parent.devicesScreen(node = node)
            }.apply {
                binding.root.layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.MATCH_PARENT
                )
            }
        },
        viewHolderBinder = { _, _, _ -> },
        viewTypeFunction = { it.ordinal }
    )
    val commandAdapter = listAdapterOf(
        initialItems = stateMachine.state.value.clientState.keys,
        viewHolderCreator = { parent, hashCode ->
            parent.recordScreen(
                node = node,
                key = stateMachine.state.value.clientState.keys.first {
                    it.hashCode() == hashCode
                }.key
            ).apply {
                binding.root.layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.MATCH_PARENT
                )
            }
        },
        viewHolderBinder = { _, _, _ -> },
        viewTypeFunction = { it.hashCode() }
    )

    binding.mainPager.apply {
        adapter = pageAdapter
        attach(binding.tabs, binding.mainPager, pageAdapter)
        registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = onPageSelected(position)
        })
        pageAdapter.submitList(stateMachine.pages)
    }

    binding.commandsPager.apply {
        adapter = commandAdapter
        attach(binding.commandTabs, binding.commandsPager, commandAdapter)
        setupForBottomSheet()
    }

    binding.root.doOnLayout {
//        scope.launch {
//            val resources = binding.root.context.resources
//            binding.liveUiState
//                .mapDistinct { it.systemUI.dynamic.run { this.topInset to this.bottomInset } }
//                .collect { (topInset, bottomInset) ->
//                    binding.bottomSheet.updateLayoutParams {
//                        this.height =
//                            it.height - topInset - resources.getDimensionPixelSize(R.dimen.double_and_half_margin)
//                    }
//                    bottomSheetBehavior.peekHeight =
//                        resources.getDimensionPixelSize(R.dimen.sextuple_margin) + bottomInset
//                }
//        }

        bottomSheetBehavior.expandedOffset = offset
        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                binding.mainPager.updatePadding(bottom = calculateTranslation(slideOffset).toInt())
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == STATE_HIDDEN && stateMachine.pages[binding.mainPager.currentItem] == Page.History) bottomSheetBehavior.state =
                    STATE_COLLAPSED
            }
        })
        onPageSelected(binding.mainPager.currentItem)
    }

    val state = stateMachine.state
    val clientState = state.mapDistinct(ControlState::clientState.asSuspend)

    val toolbarRefresh = { menu: Menu, isConnected: Boolean, selectedDevices: List<Device> ->
        menu.findItem(R.id.menu_ping)?.isVisible = isConnected
        menu.findItem(R.id.menu_connect)?.isVisible = isConnected
        menu.findItem(R.id.menu_forget)?.isVisible = !ServerNsdService.isServer

        menu.findItem(R.id.menu_rename_device)?.isVisible = selectedDevices.size == 1
        menu.findItem(R.id.menu_create_group)?.isVisible = selectedDevices.find { device ->
            device is Device.RF
        } == null
    }

    binding.root.doOnAttach {

        val toolbarMenuRefresher = binding.whileAttached { menu: Menu ->
            scope.launch {
                stateMachine.state
                    .mapDistinct { it.clientState.isConnected to it.selectedDevices }
                    .collect { (isConnected, selectedDevices) ->
                        toolbarRefresh(
                            menu,
                            isConnected,
                            selectedDevices
                        )
                    }

            }
        }

        ::uiState.updatePartial {
            copy(
                toolbarMenuRefresher = toolbarMenuRefresher,
                altToolbarMenuRefresher = toolbarMenuRefresher
            )
        }

        clientState.mapDistinct(ClientState::keys.asSuspend)
            .onEach { commandAdapter.submitList(it) }
            .launchIn(scope)

        clientState.mapDistinct(ClientState::isNew.asSuspend).onEach { isNew ->
            if (isNew) binding.commandsPager.adapter?.notifyDataSetChanged()
        }
            .launchIn(scope)

//        clientState.mapDistinct(ClientState::commandInfo.asSuspend).onEach {
//            if (it != null) ZigBeeArgumentDialogFragment.newInstance(it)
//                .show(childFragmentManager, "info")
//        }
//            .launchIn(scope)

        clientState.mapDistinct(ClientState::connectionStatus.asSuspend).onEach { status ->
//            binding::uiState.updatePartial { copy(toolbarInvalidated = true) }
            val context = binding.root.context
            binding.connectionStatus.text =
                binding.root.context.resources.getString(
                    R.string.connection_state, when (status) {
                        is Status.Connected -> context.getString(
                            R.string.connected_to,
                            status.serviceName
                        )
                        is Status.Connecting -> when (status.serviceName) {
                            null -> context.getString(R.string.connecting)
                            else -> context.getString(R.string.connecting_to, status.serviceName)
                        }
                        is Status.Disconnected -> context.getString(R.string.disconnected)
                    }
                )
        }
            .launchIn(scope)

        state.mapDistinct(ControlState::selectedDevices.asSuspend)
            .onEach {
//                binding::uiState.updatePartial { copy(toolbarInvalidated = true) }
            }
            .launchIn(scope)

        dagger.appComponent.state
            .mapDistinct { !it.isResumed }
            .onEach { stateMachine.accept(Input.Async.AppBackgrounded) }
            .launchIn(scope)
    }

//    override fun onArgsEntered(command: ZigBeeCommand) =
//        stateMachine.accept(Input.Async.ServerCommand(command.payload))
//
//
//}
}
