package com.tunjid.rcswitchcontrol.ui.root

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

fun <T, R> StateFlow<T>.mapState(scope: CoroutineScope, mapper: (T) -> R) =
    map { mapper(it) }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            initialValue = mapper(value),
            started = SharingStarted.WhileSubscribed(2000),
        )


 data class UISizes(
    val toolbarSize: Dp,
    val bottomNavSize: Dp,
    val snackbarPadding: Dp,
    val navBarHeightThreshold: Dp
)

 val uiSizes = UISizes(
    toolbarSize = 56.dp,
    bottomNavSize = 56.dp,
    snackbarPadding = 8.dp,
    navBarHeightThreshold = 80.dp
)

 infix fun Dp.countIf(condition: Boolean) = if (condition) this else 0.dp

 infix fun Int.countIf(condition: Boolean) = if (condition) this else 0
