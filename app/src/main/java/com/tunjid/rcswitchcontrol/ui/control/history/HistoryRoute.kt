package com.tunjid.rcswitchcontrol.ui.control.history

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.tunjid.globalui.UiState
import com.tunjid.mutator.Mutation
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.control.ControlViewModel
import com.tunjid.rcswitchcontrol.control.Input
import com.tunjid.rcswitchcontrol.control.Record
import com.tunjid.rcswitchcontrol.di.AppDependencies
import com.tunjid.rcswitchcontrol.di.stateMachine
import com.tunjid.rcswitchcontrol.navigation.Node
import com.tunjid.rcswitchcontrol.navigation.Route
import com.tunjid.rcswitchcontrol.ui.root.mapState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.parcelize.Parcelize

@Parcelize
data class HistoryRoute(
    val load: ClientLoad,
    val anchorToRootNode: Boolean = true
) : Route {
    @Composable
    override fun Render(node: Node) {
        val uiStateHolder = AppDependencies.current.uiStateHolder
        val navStateHolder = AppDependencies.current.navStateHolder
        val stateMachine = AppDependencies.current.stateMachine<ControlViewModel>(
            if (anchorToRootNode) navStateHolder.state.value.mainNav.root else node
        )

        val rootScope = rememberCoroutineScope()


        DisposableEffect(true) {
            uiStateHolder.accept(Mutation {
                UiState(
                    systemUI = systemUI,
                    toolbarShows = true,
                    toolbarTitle = "History",
                    showsBottomNav = true,
                )
            })
            onDispose { uiStateHolder.accept(Mutation { copy(toolbarMenuClickListener = {}) }) }
        }

        val history = remember {
            stateMachine.state.mapState(
                scope = rootScope,
                mapper = { it.clientState.history }
            )
        }

        History(history)

        LaunchedEffect(true) {
            stateMachine.accept(Input.Async.Load(load))
        }
    }

}

@Composable
private fun History(
    stateFlow: StateFlow<List<Record>>
) {
    val items by stateFlow.collectAsState()
    LazyColumn(content = {
        items(
            items = items,
            key = { it.diffId },
            itemContent = {
                Text(text = it.entry)
            }
        )
    })
}