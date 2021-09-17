package com.tunjid.rcswitchcontrol.ui.root

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
        Box {
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
            AppNavContainer(
                stateFlow = uiStateFlow.mapState(
                    scope = rootScope,
                    mapper = UiState::fragmentContainerState
                )
            ) {
                AppNavContainer(
                    navStateFlow = appStateFlow.mapState(
                        scope = rootScope,
                        mapper = AppState::nav
                    )
                )
            }
            AppBottomNav(
                stateFlow = uiStateFlow.mapState(
                    scope = rootScope,
                    mapper = UiState::bottomNavPositionalState
                )
            )
        }
    }
}