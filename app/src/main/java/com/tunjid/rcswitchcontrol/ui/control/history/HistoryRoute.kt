package com.tunjid.rcswitchcontrol.ui.control.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tunjid.globalui.UiState
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.control.ControlViewModel
import com.tunjid.rcswitchcontrol.control.Input
import com.tunjid.rcswitchcontrol.control.Record
import com.tunjid.rcswitchcontrol.di.AppDependencies
import com.tunjid.rcswitchcontrol.di.stateMachine
import com.tunjid.rcswitchcontrol.navigation.Node
import com.tunjid.rcswitchcontrol.navigation.Route
import com.tunjid.rcswitchcontrol.ui.root.InitialUiState
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
        val navStateHolder = AppDependencies.current.navStateHolder
        val stateMachine = AppDependencies.current.stateMachine<ControlViewModel>(
            if (anchorToRootNode) navStateHolder.state.value.mainNav.root else node
        )

        InitialUiState(
            UiState(
                toolbarShows = true,
                toolbarTitle = "History",
                showsBottomNav = true,
            )
        )

        val scope = rememberCoroutineScope()

        val history = remember {
            stateMachine.state.mapState(
                scope = scope,
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
            itemContent = {
                RecordCard(it)
                Spacer(modifier = Modifier.padding(4.dp))
            }
        )
    })
}

@Composable
private fun RecordCard(it: Record) {
    Text(
        modifier = Modifier
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp
            )
            .background(
                color = MaterialTheme.colors.surface,
                shape = RoundedCornerShape(10.dp)
            ),
        text = it.entry,
        color = MaterialTheme.colors.onPrimary,
    )
}