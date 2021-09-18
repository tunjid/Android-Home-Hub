package com.tunjid.rcswitchcontrol.ui.control.devices

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.tunjid.globalui.UiState
import com.tunjid.mutator.Mutation
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.control.ControlViewModel
import com.tunjid.rcswitchcontrol.control.Device
import com.tunjid.rcswitchcontrol.di.AppDependencies
import com.tunjid.rcswitchcontrol.di.stateMachine
import com.tunjid.rcswitchcontrol.navigation.Named
import com.tunjid.rcswitchcontrol.navigation.Node
import com.tunjid.rcswitchcontrol.ui.root.mapState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.parcelize.Parcelize

@Parcelize
data class DeviceRoute(
    val load: ClientLoad
): Named

@Composable
fun DevicesScreen(
    node: Node
) {
    val uiStateHolder = AppDependencies.current.uiStateHolder
    val stateMachine = AppDependencies.current.stateMachine<ControlViewModel>(node)

    val rootScope = rememberCoroutineScope()


    DisposableEffect(true) {
        uiStateHolder.accept(Mutation {
            UiState(
                systemUI = systemUI,
                toolbarShows = true,
                toolbarTitle = "Devices",
            )
        })
        onDispose { uiStateHolder.accept(Mutation { copy(toolbarMenuClickListener = {}) }) }
    }

    val devices = remember {
        stateMachine.state.mapState(
            scope = rootScope,
            mapper = { it.clientState.devices }
        )
    }

    Devices(devices)
}

@Composable
private fun Devices(
    stateFlow: StateFlow<List<Device>>
) {
    val items by stateFlow.collectAsState()
    LazyColumn(content = {
        items(
            items = items,
            key = { it.diffId },
            itemContent = {
                when (it) {
                    is Device.RF -> Unit
                    is Device.ZigBee -> ZigBeeDeviceCard(
                        device = it,
                        accept = {}
                    )
                }
            }
        )
    })
}