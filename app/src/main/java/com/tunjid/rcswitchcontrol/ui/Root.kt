package com.tunjid.rcswitchcontrol.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.BottomAppBar
import androidx.compose.material.BottomNavigation
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.tunjid.rcswitchcontrol.ui.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Composable
fun Root(
    uiStateFlow: StateFlow<UiState>
) {
    AppTheme {
        val rootScope = rememberCoroutineScope()
        Box {
            AppToolbar(stateFlow = uiStateFlow.mapState(rootScope, UiState::toolbarState))
            AppToolbar(stateFlow = uiStateFlow.mapState(rootScope, UiState::altToolbarState))
            Box() {

            }
            BottomAppBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                BottomNavigation {

                }
            }
        }
    }
}

@Composable
internal fun BoxScope.AppToolbar(stateFlow: StateFlow<ToolbarState>) {
    val state by stateFlow.collectAsState()
    val alpha: Float by animateFloatAsState(if (state.visible) 1f else 0f)

    TopAppBar(
        title = { Text(text = state.toolbarTitle.toString()) },
        modifier = Modifier
            .alpha(alpha)
            .align(Alignment.TopCenter)
            .wrapContentHeight()
            .fillMaxWidth(),
        navigationIcon = null,
        actions = {}
    )
}

fun <T, R> StateFlow<T>.mapState(scope: CoroutineScope, mapper: (T) -> R) =
    map { mapper(it) }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            initialValue = mapper(value),
            started = SharingStarted.WhileSubscribed(2000),
        )