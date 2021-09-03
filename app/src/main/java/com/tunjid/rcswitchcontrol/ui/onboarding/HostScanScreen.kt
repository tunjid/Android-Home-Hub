package com.tunjid.rcswitchcontrol.ui.onboarding

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.tunjid.globalui.ToolbarIcon
import com.tunjid.globalui.UiState
import com.tunjid.rcswitchcontrol.arch.StateMachine
import com.tunjid.rcswitchcontrol.common.Mutation
import com.tunjid.rcswitchcontrol.onboarding.Input
import com.tunjid.rcswitchcontrol.onboarding.NSDState
import com.tunjid.rcswitchcontrol.onboarding.NsdItem
import com.tunjid.rcswitchcontrol.ui.mapState
import com.tunjid.rcswitchcontrol.ui.theme.darkText
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect

private const val SCAN = 0
private const val STOP = 1
private const val REFRESH = 2

@Composable
fun HostScanScreen(
    uiStateMachine: StateMachine<Mutation<UiState>, UiState>,
    stateMachine: StateMachine<Input, NSDState>,
) {
    val rootScope = rememberCoroutineScope()
    val toolbarClicks = remember {
        mutableStateOf({ icon: ToolbarIcon ->
            when (icon.id) {
                SCAN -> stateMachine.accept(Input.StartScanning)
                STOP -> stateMachine.accept(Input.StopScanning)
            }
        })
    }

    DisposableEffect(true) {
        uiStateMachine.accept(Mutation {
            UiState(
                toolbarShows = true,
                toolbarTitle = "Home Hub",
                toolbarMenuClickListener = { icon: ToolbarIcon ->
                    when (icon.id) {
                        SCAN -> stateMachine.accept(Input.StartScanning)
                        STOP -> stateMachine.accept(Input.StopScanning)
                    }
                },
            )
        })
        onDispose { uiStateMachine.accept(Mutation { copy(toolbarMenuClickListener = {}) }) }
    }

    val isScanning = remember {
        stateMachine.state.mapState(
            scope = rootScope,
            mapper = NSDState::isScanning
        )
    }
    LaunchedEffect(true) {
        isScanning.collect { scanning ->
            uiStateMachine.accept(Mutation {
                copy(
                    toolbarIcons = listOfNotNull(
                        ToolbarIcon(id = SCAN, text = "Scan").takeIf { !scanning },
                        ToolbarIcon(id = STOP, text = "Stop").takeIf { scanning },
                        ToolbarIcon(id = REFRESH, text = "Refresh").takeIf { scanning },
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