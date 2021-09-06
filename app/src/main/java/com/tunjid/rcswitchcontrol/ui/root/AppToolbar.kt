package com.tunjid.rcswitchcontrol.ui.root

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.tunjid.globalui.ToolbarState
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun BoxScope.AppToolbar(stateFlow: StateFlow<ToolbarState>) {
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