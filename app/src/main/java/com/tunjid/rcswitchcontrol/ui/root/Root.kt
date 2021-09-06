package com.tunjid.rcswitchcontrol.ui.root

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.BottomAppBar
import androidx.compose.material.BottomNavigation
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.tunjid.globalui.*
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.di.AppState
import com.tunjid.rcswitchcontrol.di.ComposeDagger
import com.tunjid.rcswitchcontrol.navigation.StackNav
import com.tunjid.rcswitchcontrol.onboarding.HostScan
import com.tunjid.rcswitchcontrol.onboarding.Start
import com.tunjid.rcswitchcontrol.ui.onboarding.HostScanScreen
import com.tunjid.rcswitchcontrol.ui.onboarding.StartScreen
import com.tunjid.rcswitchcontrol.ui.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Composable
fun Root() {
    val appStateFlow = ComposeDagger.current.appComponent.state
    AppTheme {
        val rootScope = rememberCoroutineScope()
        val uiStateFlow = remember { appStateFlow.mapState(rootScope, AppState::ui) }

        Box {
            AppToolbar(stateFlow = uiStateFlow.mapState(rootScope, UiState::toolbarState))
            AppToolbar(stateFlow = uiStateFlow.mapState(rootScope, UiState::altToolbarState))
            ContentBox(
                stateFlow = uiStateFlow.mapState(rootScope) { it.fragmentContainerState }
            ) {
                Nav(
                    navStateFlow = appStateFlow.mapState(rootScope, AppState::nav)
                )
            }
            AppBottomNav(stateFlow = uiStateFlow.mapState(rootScope, UiState::bottomNavPositionalState))
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

    val node = nodeState.value

    when (node?.named) {
        Start -> StartScreen()
        HostScan -> HostScanScreen(node)
        is ClientLoad -> Box {

        }
        else -> Box {

        }
    }
    println("Screen: ${node?.named}")
}

@Composable
private fun BoxScope.AppToolbar(stateFlow: StateFlow<ToolbarState>) {
    val state by stateFlow.collectAsState()
    val alpha: Float by animateFloatAsState(if (state.visible) 1f else 0f)

    TopAppBar(
        title = { Text(text = state.toolbarTitle.toString()) },
        modifier = Modifier
            .alpha(alpha)
            .background(color = Color.Transparent)
            .align(Alignment.TopCenter)
            .padding(top = with(LocalDensity.current) { state.statusBarSize.toDp() })
            .wrapContentHeight()
            .fillMaxWidth(),
        navigationIcon = null,
        actions = {}
    )
}

@Composable
private fun ContentBox(
    stateFlow: StateFlow<FragmentContainerPositionalState>,
    content: @Composable BoxScope.() -> Unit
) {
    val state by stateFlow.collectAsState()

    val bottomNavHeight = uiSizes.bottomNavSize countIf state.bottomNavVisible
    val insetClearance = max(
        a = bottomNavHeight,
        b = with(LocalDensity.current) { state.keyboardSize.toDp() }
    )
    val navBarClearance = with(LocalDensity.current) {
        state.navBarSize.toDp()
    } countIf state.insetDescriptor.hasBottomInset
    val totalBottomClearance = insetClearance + navBarClearance

    val statusBarSize = with(LocalDensity.current) {
        state.statusBarSize.toDp()
    } countIf state.insetDescriptor.hasTopInset
    val toolbarHeight = uiSizes.toolbarSize countIf !state.toolbarOverlaps
    val topClearance = statusBarSize + toolbarHeight

    Box(
        modifier = Modifier.padding(
            top = topClearance,
            bottom = totalBottomClearance
        ),
        content = content
    )
}

@Composable
private fun BoxScope.AppBottomNav(
    stateFlow: StateFlow<BottomNavPositionalState>
) {
    val state by stateFlow.collectAsState()
    val bottomNavPositionAnimation = remember { Animatable(0f) }
    val navBarClearance = state.navBarSize countIf state.insetDescriptor.hasBottomInset
    val bottomNavPosition = when {
        state.bottomNavVisible -> -navBarClearance.toFloat()
        else -> with(LocalDensity.current) { uiSizes.bottomNavSize.toPx() }
    }
    LaunchedEffect(navBarClearance) {
        bottomNavPositionAnimation.animateTo(bottomNavPosition)
    }

    println("Bottom nav: ${bottomNavPositionAnimation.value}")
    BottomAppBar(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .offset(y = with(LocalDensity.current) { bottomNavPositionAnimation.value.toDp() })
            .fillMaxWidth()
            .wrapContentHeight()
    ) {

        BottomNavigation {

        }
    }
}

fun <T, R> StateFlow<T>.mapState(scope: CoroutineScope, mapper: (T) -> R) =
    map { mapper(it) }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            initialValue = mapper(value),
            started = SharingStarted.WhileSubscribed(2000),
        )


private data class UISizes(
    val toolbarSize: Dp,
    val bottomNavSize: Dp,
    val snackbarPadding: Dp,
    val navBarHeightThreshold: Dp
)

private val uiSizes = UISizes(
    toolbarSize = 56.dp,
    bottomNavSize = 56.dp,
    snackbarPadding = 8.dp,
    navBarHeightThreshold = 80.dp
)

private infix fun Dp.countIf(condition: Boolean) = if (condition) this else 0.dp

private infix fun Int.countIf(condition: Boolean) = if (condition) this else 0
