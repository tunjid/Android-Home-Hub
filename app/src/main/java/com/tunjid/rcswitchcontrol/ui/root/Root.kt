package com.tunjid.rcswitchcontrol.ui.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.tunjid.globalui.UiState
import com.tunjid.globalui.altToolbarState
import com.tunjid.globalui.bottomNavPositionalState
import com.tunjid.globalui.fragmentContainerState
import com.tunjid.globalui.toolbarState
import com.tunjid.rcswitchcontrol.di.AppDependencies
import com.tunjid.rcswitchcontrol.di.AppState
import com.tunjid.rcswitchcontrol.ui.theme.AppTheme

@Composable
fun Root() {
    val rootScope = rememberCoroutineScope()
    val appStateFlow = AppDependencies.current.state
    val uiStateFlow = remember { appStateFlow.mapState(rootScope, AppState::ui) }

    AppTheme {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            AppToolbar(
                stateFlow = uiStateFlow.mapState(
                    scope = rootScope,
                    mapper = UiState::toolbarState
                )
            )
            AppToolbar(
                stateFlow = uiStateFlow.mapState(
                    scope = rootScope,
                    mapper = UiState::altToolbarState
                )
            )
            AppRouteContainer(
                stateFlow = uiStateFlow.mapState(
                    scope = rootScope,
                    mapper = UiState::fragmentContainerState
                ),
                content = {
                    AppNavRouter(
                        navStateFlow = appStateFlow.mapState(
                            scope = rootScope,
                            mapper = AppState::nav
                        )
                    )
                }
            )
            AppBottomNav(
                stateFlow = uiStateFlow.mapState(
                    scope = rootScope,
                    mapper = UiState::bottomNavPositionalState
                )
            )
        }
    }
}