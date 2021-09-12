package com.tunjid.rcswitchcontrol.ui.onboarding

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.tunjid.globalui.ToolbarItem
import com.tunjid.globalui.UiState
import com.tunjid.mutator.Mutation
import com.tunjid.rcswitchcontrol.di.AppDependencies
import com.tunjid.rcswitchcontrol.di.stateMachine
import com.tunjid.rcswitchcontrol.navigation.Node
import com.tunjid.rcswitchcontrol.ui.hostscan.Input
import com.tunjid.rcswitchcontrol.ui.hostscan.NSDState
import com.tunjid.rcswitchcontrol.ui.hostscan.NsdItem
import com.tunjid.rcswitchcontrol.ui.hostscan.HostScanStateHolder
import com.tunjid.rcswitchcontrol.ui.root.mapState
import com.tunjid.rcswitchcontrol.ui.theme.darkText
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect

private const val SCAN = 0
private const val STOP = 1
private const val REFRESH = 2

@Composable
fun HostScanScreen(
    node: Node
) {
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
                    println("Clicked $item")
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

@Composable
private fun AvailableHosts(
    stateFlow: StateFlow<List<NsdItem>>
) {
    val items by stateFlow.collectAsState()

    LazyColumn(content = {
        items(
            count = items.size,
            key = { items[it].diffId },
            itemContent = {
                Text(
                    text = buildAnnotatedString {
                        val item = items[it]
                        append(item.info.serviceName)
                        append("\n")
                        this.withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(item.info.host.hostAddress)
                        }
                    },
                    color = darkText
                )
            }
        )
    })
}