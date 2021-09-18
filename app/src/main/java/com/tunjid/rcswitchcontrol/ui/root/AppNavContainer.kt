package com.tunjid.rcswitchcontrol.ui.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.tunjid.rcswitchcontrol.di.AppNav
import com.tunjid.rcswitchcontrol.navigation.Route
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

    when (val route = node?.named) {
        is Route -> route.Render(node)
        else -> Box {
            Text(
                modifier = Modifier
                    .padding(),
                text = "404"
            )
        }
    }
    println("Screen: ${node?.named}")
}