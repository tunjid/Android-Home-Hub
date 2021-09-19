package com.tunjid.rcswitchcontrol.ui.hostscan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tunjid.globalui.ToolbarItem
import com.tunjid.globalui.UiState
import com.tunjid.mutator.Mutation
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.di.AppDependencies
import com.tunjid.rcswitchcontrol.di.DevicesRoot
import com.tunjid.rcswitchcontrol.di.HistoryRoot
import com.tunjid.rcswitchcontrol.di.stateMachine
import com.tunjid.rcswitchcontrol.navigation.Named
import com.tunjid.rcswitchcontrol.navigation.Node
import com.tunjid.rcswitchcontrol.navigation.Route
import com.tunjid.rcswitchcontrol.navigation.StackNav
import com.tunjid.rcswitchcontrol.ui.control.devices.DeviceRoute
import com.tunjid.rcswitchcontrol.ui.control.history.HistoryRoute
import com.tunjid.rcswitchcontrol.ui.root.mapState
import com.tunjid.rcswitchcontrol.ui.theme.darkText
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.parcelize.Parcelize

private const val SCAN = 0
private const val STOP = 1
private const val REFRESH = 2

@Parcelize
object HostScan : Route {
    @Composable
    override fun Render(node: Node) {
        val uiStateHolder = AppDependencies.current.uiStateHolder
        val stateMachine = AppDependencies.current.stateMachine<HostScanStateHolder>(node)

        val rootScope = rememberCoroutineScope()


        DisposableEffect(true) {
            uiStateHolder.accept(Mutation {
                UiState(
                    systemUI = systemUI,
                    toolbarShows = true,
                    toolbarTitle = "Home Hub",
                    toolbarMenuClickListener = { item: ToolbarItem ->
                        when (item.id) {
                            SCAN -> stateMachine.accept(Input.StartScanning)
                            STOP -> stateMachine.accept(Input.StopScanning)
                        }
                    },
                )
            })
            onDispose { uiStateHolder.accept(Mutation { copy(toolbarMenuClickListener = {}) }) }
        }

        val isScanning = remember {
            stateMachine.state.mapState(
                scope = rootScope,
                mapper = NSDState::isScanning
            )
        }
        LaunchedEffect(true) {
            isScanning.collect { scanning ->
                uiStateHolder.accept(Mutation {
                    copy(
                        toolbarItems = listOfNotNull(
                            ToolbarItem(id = SCAN, text = "Scan").takeIf { !scanning },
                            ToolbarItem(id = STOP, text = "Stop").takeIf { scanning },
                            ToolbarItem(id = REFRESH, text = "Refresh").takeIf { scanning },
                        )
                    )
                })
            }
        }

        val items = remember {
            stateMachine.state.mapState(
                scope = rootScope,
                mapper = NSDState::items
            )
        }
        AvailableHosts(items)
    }
}

@Composable
private fun AvailableHosts(
    stateFlow: StateFlow<List<NsdItem>>
) {
    val items by stateFlow.collectAsState()
    val navStateHolder = AppDependencies.current.navStateHolder

    LazyColumn(content = {
        items(
            items = items,
            key = NsdItem::diffId,
            itemContent = { nsdItem ->
                Text(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable {
                            navStateHolder.accept(Mutation {
                                copy(
                                    mainNav = mainNav.copy(
                                        children = listOf(
                                            StackNav(
                                                root = DevicesRoot,
                                            ).push(Node(DeviceRoute(ClientLoad.NewClient(nsdItem.info)))),
                                            StackNav(
                                                root = HistoryRoot,
                                            ).push(Node(HistoryRoute(ClientLoad.NewClient(nsdItem.info)))),
                                        )
                                    )
                                )
                            })
                        },
                    text = buildAnnotatedString {
                        append(nsdItem.info.serviceName)
                        append("\n")
                        this.withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(nsdItem.info.host.hostAddress)
                        }
                    },
                    color = darkText,
                )
            }
        )
    })
}