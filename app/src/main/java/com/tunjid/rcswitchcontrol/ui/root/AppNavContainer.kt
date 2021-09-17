package com.tunjid.rcswitchcontrol.ui.root

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.di.AppNav
import com.tunjid.rcswitchcontrol.onboarding.HostScan
import com.tunjid.rcswitchcontrol.onboarding.Start
import com.tunjid.rcswitchcontrol.ui.onboarding.HostScanScreen
import com.tunjid.rcswitchcontrol.ui.start.StartScreen
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun AppNavContainer(
    navStateFlow: StateFlow<AppNav>
) {
    val scope = rememberCoroutineScope()
    val nodeState = navStateFlow
        .mapState(scope, AppNav::currentNode)
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