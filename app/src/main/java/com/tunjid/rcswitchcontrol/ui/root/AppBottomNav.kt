package com.tunjid.rcswitchcontrol.ui.root

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.BottomAppBar
import androidx.compose.material.BottomNavigation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.tunjid.globalui.BottomNavPositionalState
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun BoxScope.AppBottomNav(
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