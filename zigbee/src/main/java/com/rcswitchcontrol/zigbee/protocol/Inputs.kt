package com.rcswitchcontrol.zigbee.protocol

import com.rcswitchcontrol.protocols.CommonDeviceActions
import com.rcswitchcontrol.protocols.Name
import com.rcswitchcontrol.protocols.io.ConsoleStream
import com.rcswitchcontrol.zigbee.commands.PayloadPublishingCommand
import com.rcswitchcontrol.zigbee.models.ZigBeeCommand
import com.rcswitchcontrol.zigbee.models.ZigBeeInput
import com.rcswitchcontrol.zigbee.models.payload
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol.Companion.zigBeePayload
import com.tunjid.rcswitchcontrol.common.ReactivePreference
import com.tunjid.rcswitchcontrol.common.asSuspend
import com.tunjid.rcswitchcontrol.common.distinctBy
import com.tunjid.rcswitchcontrol.common.serialize
import com.tunjid.rcswitchcontrol.common.takeUntil
import com.tunjid.rcswitchcontrol.common.withLatestFrom
import com.zsmartsystems.zigbee.ZigBeeNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal fun ZigBeeProtocol.processInputs(inputs: Flow<Action.Input>): Flow<Action.Output> {
    val start = inputs
        .filterIsInstance<Action.Input.InitializationStatus.Initialized>()
        .shareIn(scope = scope, started = SharingStarted.WhileSubscribed())

    return merge(
        handleCommandInputs(inputs, start, payloadStream, responseStream, sharedDispatcher),
        handleAttributeRequests(inputs),
        handleNodeAdds(inputs, start),
    )
}

private fun ZigBeeProtocol.handleCommandInputs(
    actions: Flow<Action.Input>,
    start: Flow<Action.Input.InitializationStatus.Initialized>,
    payloadStream: ConsoleStream,
    responseStream: ConsoleStream,
    sharedScheduler: CoroutineDispatcher
): Flow<Action.Output> = actions
    .filterIsInstance<Action.Input.CommandInput>()
    .withLatestFrom(start, ::Pair)
    .map { (input, state) ->
        val (consoleCommand, args) = input
        scope.launch(sharedScheduler) {
            consoleCommand.process(
                state.networkManager,
                args.toTypedArray(),
                if (consoleCommand is PayloadPublishingCommand) payloadStream else responseStream
            )
        }
    }
    .flowOn(sharedScheduler)
    .filterIsInstance()

private fun handleAttributeRequests(
    actions: Flow<Action.Input>,
): Flow<Action.Output.PayloadReprocess> = actions
    .filterIsInstance<Action.Input.AttributeRequest>()
    .flatMapConcat { it.nodes.asFlow() }
    .flatMapConcat { node ->
        node.supportedFeatures
            .map { ZigBeeInput.Read(it) }
            .map { it.commandFor(node) }
            .map(ZigBeeCommand::payload)
            .asFlow()
    }
    .map(Action.Output::PayloadReprocess)

private fun handleNodeAdds(
    actions: Flow<Action.Input>,
    start: Flow<Action.Input.InitializationStatus.Initialized>
): Flow<Action.Output> = actions
    .filterIsInstance<Action.Input.NodeChange.Added>()
    .map(Action.Input.NodeChange.Added::node.asSuspend)
    .distinctBy(ZigBeeNode::getIeeeAddress)
    .withLatestFrom(start, ::Pair)
    .flatMapMerge(concurrency = Int.MAX_VALUE) { (node, state) ->
        val id = node.ieeeAddress.toString()
        ReactivePreference(
            reactivePreferences = state.startAction.deviceNames,
            key = id,
            default = id
        )
            .monitor
            .flatMapConcat { deviceName ->
                listOf(
                    Action.Output.Log("Device name changed. id: ${node.ieeeAddress}; new name: $deviceName"),
                    Action.Output.PayloadOutput(zigBeePayload(
                        data = Name(id = id, key = ZigBeeProtocol.key, value = deviceName).serialize(),
                        action = CommonDeviceActions.nameChangedAction
                    ))
                ).asFlow()
            }
            .takeUntil(actions.filterIsInstance<Action.Input.NodeChange.Removed>())
    }
