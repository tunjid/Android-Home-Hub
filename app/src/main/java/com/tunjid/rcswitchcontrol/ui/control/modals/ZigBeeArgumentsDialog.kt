package com.tunjid.rcswitchcontrol.ui.control.modals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.rcswitchcontrol.zigbee.models.ZigBeeCommand
import com.rcswitchcontrol.zigbee.models.ZigBeeCommandInfo
import com.tunjid.mutator.Mutation
import com.tunjid.mutator.scopedStateHolder
import kotlinx.coroutines.flow.StateFlow

private data class State(
    val isOpen: Boolean = false,
    val currentInfo: ZigBeeCommandInfo? = null
)

@Composable
fun ZigBeeArgumentDialog(
    stateFlow: StateFlow<ZigBeeCommandInfo?>,
    onCommandEntered: (ZigBeeCommand) -> Unit
) {
    val scope = rememberCoroutineScope()
    val stateHolder = remember {
        scopedStateHolder<Mutation<State>, State>(
            scope = scope,
            initialState = State(),
            transform = { it }
        )
    }

    val commandInfo by stateFlow.collectAsState()
    val state by stateHolder.state.collectAsState()

    LaunchedEffect(commandInfo) {
        if (commandInfo != null) stateHolder.accept(Mutation {
            copy(isOpen = true, currentInfo = commandInfo)
        })
    }

    if (state.isOpen) AlertDialog(
        onDismissRequest = {
            stateHolder.accept(Mutation { copy(isOpen = false) })
        },
        title = {
            Text(text = "Command: ${commandInfo?.command}")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                state.currentInfo?.entries?.forEach { entry ->
                    TextField(
                        value = entry.value,
                        onValueChange = {
                            stateHolder.accept(Mutation {
                                copy(currentInfo = currentInfo?.updateEntry(entry.copy(value = it)))
                            })
                        },
                        label = { Text(entry.key) },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    stateHolder.state.value.currentInfo?.let { onCommandEntered(it.toArgs()) }
                    stateHolder.accept(Mutation { copy(isOpen = false) })
                }) {
                Text("Okay")
            }
        },
        dismissButton = {
            Button(
                onClick = {
                    stateHolder.accept(Mutation { copy(isOpen = false) })
                }) {
                Text("Dismiss")
            }
        }
    )
}