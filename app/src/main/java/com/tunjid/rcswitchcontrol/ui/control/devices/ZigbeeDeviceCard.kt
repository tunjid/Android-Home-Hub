package com.tunjid.rcswitchcontrol.ui.control.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rcswitchcontrol.zigbee.models.ZigBeeCommand
import com.rcswitchcontrol.zigbee.models.ZigBeeInput
import com.rcswitchcontrol.zigbee.models.ZigBeeNode
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.control.Device
import com.tunjid.rcswitchcontrol.control.Throttle
import com.tunjid.rcswitchcontrol.control.isCoordinator
import com.tunjid.rcswitchcontrol.control.level

@Composable
fun ZigBeeDeviceCard(
    device: Device.ZigBee,
    accept: (ZigBeeCommand) -> Unit
) {

    val throttle = remember {
        Throttle { accept(device.node.command(ZigBeeInput.Level(level = it / 100F))) }
    }

    val dialogState = remember { mutableStateOf(false) }

    if (dialogState.value) ZigBeeOptions(
        device = device,
        accept = accept,
        dismiss = { dialogState.value = false }
    )

    Surface(
        modifier = Modifier.padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = 8.dp,
    ) {
        Column {
            Row {
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                ZigBeeIcon(
                    resourceId = R.drawable.ic_zigbee_24dp,
                ) { dialogState.value = true }
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                if (device.node.supports(ZigBeeNode.Feature.Color)) ZigBeeIcon(
                    resourceId = R.drawable.ic_palette_24dp
                ) {}
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                Text(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    text = device.name,
                    color = MaterialTheme.colors.onPrimary
                )
                if (device.node.supports(ZigBeeNode.Feature.OnOff)) Switch(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    checked = device.isSelected,
                    onCheckedChange = { isChecked ->
                        accept(device.node.command(ZigBeeInput.Toggle(isOn = isChecked)))
                    }
                )
            }
            if (device.node.supports(ZigBeeNode.Feature.OnOff)) Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                Button(
                    modifier = Modifier
                        .padding(horizontal = 16.dp),
                    onClick = {
                        accept(device.node.command(ZigBeeInput.Toggle(isOn = true)))
                    },
                    content = {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = 16.dp),
                            text = "On",
                            color = MaterialTheme.colors.onPrimary
                        )
                    }
                )

                Button(
                    modifier = Modifier
                        .padding(horizontal = 16.dp),
                    onClick = {
                        accept(device.node.command(ZigBeeInput.Toggle(isOn = false)))
                    },
                    content = {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = 16.dp),
                            text = "Off",
                            color = MaterialTheme.colors.onPrimary
                        )
                    }
                )
            }
            if (device.node.supports(ZigBeeNode.Feature.Level)) Slider(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = SliderDefaults.colors(),
                value = device.level ?: 0f,
                valueRange = 1f..100f,
                onValueChange = { throttle.run(it.toInt()) }
            )
        }
    }
}

@Composable
private fun ZigBeeIcon(
    resourceId: Int,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier
            .wrapContentSize(align = Alignment.CenterEnd)
            .padding(vertical = 16.dp),
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colors.onPrimary),
        onClick = onClick,
        content = {
            Image(
                modifier = Modifier.align(alignment = Alignment.CenterVertically),
                painter = painterResource(id = resourceId),
                contentDescription = ""
            )
        }
    )
}

@Composable
private fun ZigBeeOptions(
    device: Device.ZigBee,
    accept: (ZigBeeCommand) -> Unit,
    dismiss: () -> Unit
) {
    val diagnosticOptions = remember {
        listOfNotNull(
            "Node" to ZigBeeInput.Node,
            "Rediscover" to ZigBeeInput.Rediscover,
            ("Enable Join (60s)" to ZigBeeInput.Join(duration = 60))
                .takeIf { device.isCoordinator },
            ("Disable Join" to ZigBeeInput.Join(duration = null))
                .takeIf { device.isCoordinator },
            ("Subscribe" to ZigBeeInput.Subscribe(ZigBeeNode.Feature.OnOff))
                .takeIf { !device.isCoordinator },
        )
            .plus(device.node.supportedFeatures.map { it.text to ZigBeeInput.Read(it) })
            .map {
                when (it.second) {
                    is ZigBeeInput.Read -> "Read ${it.first}"
                    else -> it.first
                } to device.node.command(it.second)
            }
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(
                modifier = Modifier.padding(vertical = 8.dp),
                text = "Choose ZigBee Command"
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                diagnosticOptions.forEach { (text, command) ->
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        content = { Text(text = text) },
                        onClick = {
                            accept(command)
                            dismiss()
                        }
                    )
                    Spacer(modifier = Modifier.padding(8.dp))
                }
            }
        },
        buttons = {}
    )
}