package com.tunjid.rcswitchcontrol.ui.control.devices

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rcswitchcontrol.zigbee.models.ZigBeeCommand
import com.rcswitchcontrol.zigbee.models.payload
import com.tunjid.globalui.ToolbarItem
import com.tunjid.globalui.UiState
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.control.ControlViewModel
import com.tunjid.rcswitchcontrol.control.Device
import com.tunjid.rcswitchcontrol.control.Input
import com.tunjid.rcswitchcontrol.di.AppDependencies
import com.tunjid.rcswitchcontrol.di.stateMachine
import com.tunjid.rcswitchcontrol.navigation.Node
import com.tunjid.rcswitchcontrol.navigation.Route
import com.tunjid.rcswitchcontrol.ui.root.InitialUiState
import com.tunjid.rcswitchcontrol.ui.root.mapState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.parcelize.Parcelize

@Parcelize
data class DeviceRoute(
    val load: ClientLoad,
    val anchorToRootNode: Boolean = true
) : Route {
    @Composable
    override fun Render(node: Node) {
        val navStateHolder = AppDependencies.current.navStateHolder
        val stateMachine = AppDependencies.current.stateMachine<ControlViewModel>(
            if (anchorToRootNode) navStateHolder.state.value.mainNav.root else node
        )

        InitialUiState(
            UiState(
                toolbarShows = true,
                toolbarTitle = "Devices",
                showsBottomNav = true,
                toolbarItems = listOf(
                    ToolbarItem(id = 0, text = "Ping"),
                    ToolbarItem(id = 1, text = "Debug")
                ),
                toolbarMenuClickListener = {
                    when (it.id) {
                        0 -> stateMachine.accept(Input.Async.PingServer)
                        1 -> println("State: ${stateMachine.state.value.clientState}")
                    }
                }
            )
        )

        val scope = rememberCoroutineScope()

        val devices = remember {
            stateMachine.state.mapState(
                scope = scope,
                mapper = { it.clientState.devices }
            )
        }

        Devices(
            stateFlow = devices,
            onCommandEntered = { stateMachine.accept(Input.Async.ServerCommand(it.payload)) }
        )

        LaunchedEffect(true) {
            stateMachine.accept(Input.Async.Load(load))
        }
    }

}

@Composable
private fun Devices(
    stateFlow: StateFlow<List<Device>>,
    onCommandEntered: (ZigBeeCommand) -> Unit
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
                        accept = onCommandEntered
                    )
                }
                Spacer(modifier = Modifier.padding(16.dp))
            }
        )
    })
}