/*
 * MIT License
 *
 * Copyright (c) 2019 Adetunji Dahunsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.rcswitchcontrol.zigbee.protocol

import android.content.Context
import android.hardware.usb.UsbDevice
import com.rcswitchcontrol.protocols.CommonDeviceActions
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.Name
import com.rcswitchcontrol.protocols.asAction
import com.rcswitchcontrol.protocols.io.ConsoleStream
import com.rcswitchcontrol.protocols.models.Payload
import com.rcswitchcontrol.zigbee.R
import com.rcswitchcontrol.zigbee.io.AndroidZigBeeSerialPort
import com.rcswitchcontrol.zigbee.models.ZigBeeCommand
import com.rcswitchcontrol.zigbee.models.ZigBeeCommandInfo
import com.rcswitchcontrol.zigbee.persistence.ZigBeeDataStore
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol.Companion.zigBeePayload
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.tunjid.rcswitchcontrol.common.ReactivePreference
import com.tunjid.rcswitchcontrol.common.ReactivePreferences
import com.tunjid.rcswitchcontrol.common.SerialInfo
import com.tunjid.rcswitchcontrol.common.deserialize
import com.tunjid.rcswitchcontrol.common.serialize
import com.tunjid.rcswitchcontrol.common.serializeList
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.ZigBeeNode
import com.zsmartsystems.zigbee.console.ZigBeeConsoleCommand
import com.zsmartsystems.zigbee.dongle.cc2531.ZigBeeDongleTiCc2531
import io.reactivex.Flowable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.processors.PublishProcessor
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import java.io.PrintWriter
import java.util.concurrent.TimeUnit

private typealias Node = com.rcswitchcontrol.zigbee.models.ZigBeeNode

internal sealed class Action {
    sealed class Input : Action() {

        data class Start(
            val deviceNames: ReactivePreferences,
            val dongle: Dongle,
            val dataStoreName: String
        ) : Input()

        internal sealed class InitializationStatus : Input() {
            data class Initialized(
                val startAction: Start,
                val dataStore: ZigBeeDataStore,
                val networkManager: ZigBeeNetworkManager,
                val inputs: PublishProcessor<Input>,
                val outputs: Flowable<Output>,
            ) : InitializationStatus()

            object Error : InitializationStatus()
        }

        sealed class NodeChange : Input() {
            data class Added(val node: ZigBeeNode) : NodeChange()
            data class Removed(val node: ZigBeeNode) : NodeChange()
        }

        data class CommandInput(
            val command: ZigBeeConsoleCommand,
            val args: List<String>
        ) : Input()

        data class AttributeRequest(val nodes: List<Node>) : Input()
    }

    sealed class Output : Action() {
        data class PayloadReprocess(val payload: Payload) : Output()
        data class PayloadOutput(val payload: Payload) : Output()
        data class Log(val message: String) : Output()
    }
}

private val Action.Output.Log.payload get() = zigBeePayload(response = message)


@Suppress("PrivatePropertyName")
class ZigBeeProtocol(
    context: Context,
    dongle: Dongle,
    override val printWriter: PrintWriter
) : CommsProtocol {

    private val payloadOutputProcessor = PublishProcessor.create<Payload>()
    private val disposable = CompositeDisposable()

    internal val responseStream = ConsoleStream { response ->
        zigBeePayload(response = response)
            .let(payloadOutputProcessor::onNext)
    }
    internal val payloadStream = ConsoleStream {
        it.takeIf(String::isNotBlank)
            ?.deserialize<Payload>()
            ?.let(payloadOutputProcessor::onNext)
    }

    private val initializationStatus: Action.Input.InitializationStatus = initialize(Action.Input.Start(
        deviceNames = ReactivePreferences(context.getSharedPreferences("device names", Context.MODE_PRIVATE)),
        dongle = dongle,
        dataStoreName = "47",
    ))

    init {
        when (val status = initializationStatus) {
            Action.Input.InitializationStatus.Error -> Unit
            is Action.Input.InitializationStatus.Initialized -> {
                processInputs(status.inputs)
                    .mergeWith(status.outputs)
                    .subscribe { output ->
                        when (output) {
                            is Action.Output.PayloadReprocess -> processInput(output.payload)
                            is Action.Output.PayloadOutput -> payloadOutputProcessor.onNext(output.payload)
                            is Action.Output.Log -> payloadOutputProcessor.onNext(output.payload).also { println(output) }
                        }
                    }
                    .addTo(disposable)

                val sharedScheduler = Schedulers.from(CommsProtocol.sharedPool)

                payloadOutputProcessor
                    .onBackpressureBuffer()
                    .concatMap { payload ->
                        Flowable.just(payload)
                            .delay(OUTPUT_BUFFER_RATE, TimeUnit.MILLISECONDS, sharedScheduler)
                    }
                    .subscribeOn(sharedScheduler)
                    .observeOn(sharedScheduler)
                    .subscribe(this::pushOut)
                    .addTo(disposable)

                status.inputs.onNext(status)
            }
        }
    }

    override fun processInput(payload: Payload): Payload = when (val status = initializationStatus) {
        Action.Input.InitializationStatus.Error -> zigBeePayload(response = "Initialization failed, protocol unavailable")
        is Action.Input.InitializationStatus.Initialized -> zigBeePayload().apply {
            addCommand(CommsProtocol.resetAction)

            when (val payloadAction = payload.action) {
                null -> response = "Unrecognized command $payloadAction"
//                CommsProtocol.resetAction -> reset()
                formNetworkAction -> {
                    response = ContextProvider.appContext.getString(R.string.zigbeeprotocol_forming_network)
//                    formNetwork()
                }
                CommsProtocol.pingAction -> {
                    response = ContextProvider.appContext.getString(R.string.zigbeeprotocol_ping)
                }
                CommonDeviceActions.refreshDevicesAction -> {
                    val savedDevices = status.dataStore.savedDevices
                    action = CommonDeviceActions.refreshDevicesAction
                    response = ContextProvider.appContext.getString(R.string.zigbeeprotocol_saved_devices_request)
                    data = savedDevices.serializeList()
                    status.inputs.onNext(Action.Input.AttributeRequest(nodes = savedDevices))
                }
                CommonDeviceActions.renameAction -> when (val newName = payload.data?.deserialize<Name>()) {
                    null -> Unit
                    else -> ReactivePreference(
                        reactivePreferences = status.startAction.deviceNames,
                        key = newName.id,
                        default = newName.id
                    ).value = newName.value
                }
                in availableCommands.keys -> {
                    val mapper = availableCommands.getValue(payloadAction)
                    val consoleCommand = mapper.consoleCommand
                    val command = payload.data?.deserialize<ZigBeeCommand>()
                    val needsCommandArgs: Boolean = (command == null || command.isInvalid) && consoleCommand.syntax.isNotEmpty()

                    when {
                        needsCommandArgs -> {
                            action = commandInfoAction
                            response = ContextProvider.appContext.getString(R.string.zigbeeprotocol_enter_args, payloadAction)
                            data = ZigBeeCommandInfo(payloadAction.value, consoleCommand.description, consoleCommand.syntax, consoleCommand.help).serialize()
                        }
                        else -> {
                            val args = command?.args ?: listOf(payloadAction.value)
                            response = ContextProvider.appContext.getString(R.string.zigbeeprotocol_executing, args.commandString())
                            status.inputs.onNext(Action.Input.CommandInput(mapper.consoleCommand, args))
                        }
                    }
                }
                else -> response = "Unrecognized command $payloadAction"
            }
        }
    }

    override fun close() {
        disposable.clear()
        when (val status = initializationStatus) {
            is Action.Input.InitializationStatus.Initialized -> status.networkManager.shutdown()
            Action.Input.InitializationStatus.Error -> Unit
        }
    }

    companion object {
        const val MESH_UPDATE_PERIOD = 20
        const val OUTPUT_BUFFER_RATE = 100L

        val key = CommsProtocol.Key(ZigBeeProtocol::class.java.name)

        internal val formNetworkAction get() = CommsProtocol.Action(ContextProvider.appContext.getString(R.string.zigbeeprotocol_formnet))

        val commandInfoAction: CommsProtocol.Action get() = "ZigBeeCommandInfo".asAction
        val attributeCarryingActions: List<CommsProtocol.Action>
            get() = listOf(
                NamedCommand.Custom.On.action,
                NamedCommand.Custom.Off.action,
                NamedCommand.Custom.Level.action,
                NamedCommand.Custom.Color.action,
                NamedCommand.Custom.DeviceAttributes.action,
            )

        internal fun List<String>.commandString() = joinToString(separator = " ")

        internal fun zigBeePayload(
            data: String? = null,
            action: CommsProtocol.Action? = null,
            response: String? = null
        ) = Payload(
            key = key,
            data = data,
            action = action,
            response = response
        ).appendZigBeeCommands()
    }
}
