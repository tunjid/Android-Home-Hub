package com.rcswitchcontrol.zigbee.protocol

import com.jakewharton.rx.replayingShare
import com.rcswitchcontrol.protocols.CommonDeviceActions
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.Name
import com.rcswitchcontrol.protocols.io.ConsoleStream
import com.rcswitchcontrol.zigbee.commands.PayloadPublishingCommand
import com.rcswitchcontrol.zigbee.models.ZigBeeCommand
import com.rcswitchcontrol.zigbee.models.ZigBeeInput
import com.rcswitchcontrol.zigbee.models.payload
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol.Companion.zigBeePayload
import com.tunjid.rcswitchcontrol.common.ReactivePreference
import com.tunjid.rcswitchcontrol.common.filterIsInstance
import com.tunjid.rcswitchcontrol.common.serialize
import com.zsmartsystems.zigbee.ZigBeeNode
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers

internal fun ZigBeeProtocol.processInputs(inputs: Flowable<Action.Input>): Flowable<Action.Output> {
    val sharedScheduler = Schedulers.from(CommsProtocol.sharedPool)

    val start = inputs
        .filterIsInstance<Action.Input.InitializationStatus.Initialized>()
        .replayingShare()

    return Flowable.merge(listOf(
        handleCommandInputs(inputs, start, payloadStream, responseStream, sharedScheduler),
        handleAttributeRequests(inputs),
        handleNodeAdds(inputs, start),
    ))
}

private fun handleCommandInputs(
    actions: Flowable<Action.Input>,
    start: Flowable<Action.Input.InitializationStatus.Initialized>,
    payloadStream: ConsoleStream,
    responseStream: ConsoleStream,
    sharedScheduler: Scheduler
): Flowable<Action.Output> = actions
    .filterIsInstance<Action.Input.CommandInput>()
    .onBackpressureBuffer()
    .withLatestFrom(start, ::Pair)
    .concatMapCompletable { (input, state) ->
        val (consoleCommand, args) = input
        Completable.fromCallable {
            consoleCommand.process(
                state.networkManager,
                args.toTypedArray(),
                if (consoleCommand is PayloadPublishingCommand) payloadStream else responseStream
            )
        }
            .onErrorComplete()
            .subscribeOn(sharedScheduler)
    }
    .toFlowable()

private fun handleAttributeRequests(
    actions: Flowable<Action.Input>,
): Flowable<Action.Output.PayloadReprocess> = actions
    .filterIsInstance<Action.Input.AttributeRequest>()
    .concatMap { Flowable.fromIterable(it.nodes) }
    .concatMap { node ->
        Flowable.fromIterable(node.supportedFeatures
            .map { ZigBeeInput.Read(it) }
            .map { it.commandFor(node) }
            .map(ZigBeeCommand::payload)
        )
    }
    .map(Action.Output::PayloadReprocess)

private fun handleNodeAdds(
    actions: Flowable<Action.Input>,
    start: Flowable<Action.Input.InitializationStatus.Initialized>
): Flowable<Action.Output> = actions
    .filterIsInstance<Action.Input.NodeChange.Added>()
    .map(Action.Input.NodeChange.Added::node)
    .distinct(ZigBeeNode::getIeeeAddress)
    .withLatestFrom(start, ::Pair)
    .flatMap { (node, state) ->
        val id = node.ieeeAddress.toString()
        ReactivePreference(
            reactivePreferences = state.startAction.deviceNames,
            key = id,
            default = id
        )
            .monitor
            .concatMap { deviceName ->
                Flowable.just(
                    Action.Output.Log("Device name changed. id: ${node.ieeeAddress}; new name: $deviceName"),
                    Action.Output.PayloadOutput(zigBeePayload(
                        data = Name(id = id, key = ZigBeeProtocol.key, value = deviceName).serialize(),
                        action = CommonDeviceActions.nameChangedAction
                    ))
                )
            }
            .takeUntil(actions.filterIsInstance<Action.Input.NodeChange.Removed>())
    }
