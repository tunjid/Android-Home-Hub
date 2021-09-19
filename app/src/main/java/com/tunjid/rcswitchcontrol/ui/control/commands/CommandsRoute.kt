package com.tunjid.rcswitchcontrol.ui.control.commands

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Tab
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.pagerTabIndicatorOffset
import com.google.accompanist.pager.rememberPagerState
import com.rcswitchcontrol.protocols.CommsProtocol
import com.tunjid.globalui.UiState
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.client.name
import com.tunjid.rcswitchcontrol.control.ControlViewModel
import com.tunjid.rcswitchcontrol.control.Input
import com.tunjid.rcswitchcontrol.control.Record
import com.tunjid.rcswitchcontrol.di.AppDependencies
import com.tunjid.rcswitchcontrol.di.stateMachine
import com.tunjid.rcswitchcontrol.navigation.Node
import com.tunjid.rcswitchcontrol.navigation.Route
import com.tunjid.rcswitchcontrol.ui.control.history.RecordCard
import com.tunjid.rcswitchcontrol.ui.root.InitialUiState
import com.tunjid.rcswitchcontrol.ui.root.mapState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.parcelize.Parcelize

@Parcelize
data class CommandsRoute(
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
                toolbarTitle = "Commands",
                showsBottomNav = true,
            )
        )

        val scope = rememberCoroutineScope()

        val commands = remember {
            stateMachine.state.mapState(
                scope = scope,
                mapper = {
                    it.clientState.commands
                        .toList()
                        .sortedBy { it.first.name }
                }
            )
        }

        CommandsPager(commands)

        LaunchedEffect(true) {
            stateMachine.accept(Input.Async.Load(load))
        }
    }

}

@Composable
@OptIn(ExperimentalPagerApi::class)
private fun CommandsPager(
    stateFlow: StateFlow<List<Pair<CommsProtocol.Key, List<Record.Command>>>>
) {
    val entries by stateFlow.collectAsState()
    if (entries.isNotEmpty()) {
        val pagerState = rememberPagerState(pageCount = entries.size)

        Column(modifier = Modifier.fillMaxSize()) {
            ScrollableTabRow(
                // Our selected tab is our current page
                selectedTabIndex = pagerState.currentPage,
                // Override the indicator, using the provided pagerTabIndicatorOffset modifier
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.pagerTabIndicatorOffset(pagerState, tabPositions)
                    )
                }
            ) {
                // Add tabs for all of our pages
                entries.forEachIndexed { index, entry ->
                    Tab(
                        text = { Text(text = entry.first.name) },
                        selected = pagerState.currentPage == index,
                        onClick = { /* TODO */ },
                    )
                }
            }

            HorizontalPager(state = pagerState) { page ->
                CommandPage(commands = entries[page].second)
            }
        }

    }
}

@Composable
private fun CommandPage(commands: List<Record.Command>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth()
    ) {
        commands.forEach {
            RecordCard(it)
            Spacer(modifier = Modifier.padding(2.dp))
        }
    }

}