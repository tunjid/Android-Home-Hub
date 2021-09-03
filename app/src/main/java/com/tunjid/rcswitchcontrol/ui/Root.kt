package com.tunjid.rcswitchcontrol.ui

import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.BottomAppBar
import androidx.compose.material.BottomNavigation
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.viewinterop.AndroidView
import com.tunjid.globalui.ToolbarState
import com.tunjid.globalui.UiState
import com.tunjid.globalui.altToolbarState
import com.tunjid.globalui.toolbarState
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.control.controlScreen
import com.tunjid.rcswitchcontrol.di.AppState
import com.tunjid.rcswitchcontrol.navigation.StackNav
import com.tunjid.rcswitchcontrol.onboarding.HostScan
import com.tunjid.rcswitchcontrol.onboarding.Start
import com.tunjid.rcswitchcontrol.onboarding.hostScanScreen
import com.tunjid.rcswitchcontrol.onboarding.startScreen
import com.tunjid.rcswitchcontrol.ui.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Composable
fun Root(
    stateFlow: StateFlow<AppState>
) {
    AppTheme {
        val rootScope = rememberCoroutineScope()
        val uiStateFlow = remember { stateFlow.mapState(rootScope, AppState::ui) }
        val navStateFlow = remember { stateFlow.mapState(rootScope, AppState::nav) }

        Box {
            AppToolbar(stateFlow = uiStateFlow.mapState(rootScope, UiState::toolbarState))
            AppToolbar(stateFlow = uiStateFlow.mapState(rootScope, UiState::altToolbarState))
            Nav(
                navStateFlow = navStateFlow
            )
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
fun Nav(
    navStateFlow: StateFlow<StackNav>
) {
    val scope = rememberCoroutineScope()
    val nodeState = navStateFlow
        .mapState(scope, StackNav::currentNode)
        .collectAsState()

    // Adds view to Compose
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            FrameLayout(context)
        },
        update = { container ->
            container.apply {
                removeAllViews()
                val node = nodeState.value
                val screen = when (val named = node?.named) {
                    Start -> startScreen()
                    HostScan -> hostScanScreen(node)
                    is ClientLoad -> controlScreen(node, named)
                    else -> null
                }
                println("Screen: ${node?.named}")
                if (screen != null) addView(screen.binding.root)
            }
        }
    )
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