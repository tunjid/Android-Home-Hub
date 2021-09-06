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
import com.tunjid.rcswitchcontrol.di.AppState
import com.tunjid.rcswitchcontrol.di.ComposeDagger
import com.tunjid.rcswitchcontrol.ui.theme.AppTheme

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
                AppNav(
                    navStateFlow = appStateFlow.mapState(rootScope, AppState::nav)
                )
            }
            AppBottomNav(
                stateFlow = uiStateFlow.mapState(
                    rootScope,
                    UiState::bottomNavPositionalState
                )
            )
        }
    }
}