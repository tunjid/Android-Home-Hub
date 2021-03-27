package com.rcswitchcontrol.zigbee.protocol

import android.util.Log
import com.jakewharton.rx.replayingShare
import com.rcswitchcontrol.protocols.CommonDeviceActions
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.Name
import com.rcswitchcontrol.protocols.io.ConsoleStream
import com.rcswitchcontrol.protocols.models.Payload
import com.rcswitchcontrol.zigbee.commands.PayloadPublishingCommand
import com.rcswitchcontrol.zigbee.models.ZigBeeCommand
import com.rcswitchcontrol.zigbee.models.ZigBeeInput
import com.rcswitchcontrol.zigbee.models.payload
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol.Companion.commandString
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol.Companion.zigBeePayload
import com.tunjid.rcswitchcontrol.common.ReactivePreference
import com.tunjid.rcswitchcontrol.common.deserialize
import com.tunjid.rcswitchcontrol.common.filterIsInstance
import com.tunjid.rcswitchcontrol.common.serialize
import com.zsmartsystems.zigbee.ZigBeeNode
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.processors.PublishProcessor
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

internal fun ZigBeeProtocol.processg(actions: PublishProcessor<Action>): Disposable {

    val disposable = CompositeDisposable()

    val sharedScheduler = Schedulers.from(CommsProtocol.sharedPool)

    val post = { messages: Array<out String> ->
        actions.onNext(Action.Output.PayloadOutput(zigBeePayload(
            response = messages.toList().commandString()
        )))
    }

    val responseStream = ConsoleStream { post(arrayOf(it)) }
    val payloadStream = ConsoleStream {
        it.takeIf(String::isNotBlank)
            ?.deserialize<Payload>()
            ?.let(Action.Output::PayloadOutput)
            ?.let(actions::onNext)
    }

    val start = actions
        .filterIsInstance<Action.Input.Start>()
        .replayingShare()

    actions
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
        .subscribe()
        .addTo(disposable)

    actions
        .filterIsInstance<Action.Input.AttributeRequest>()
        .concatMap { Flowable.fromIterable(it.nodes) }
        .concatMap { node ->
            Flowable.fromIterable(node.supportedFeatures
                .map { ZigBeeInput.Read(it) }
                .map { it.commandFor(node) }
                .map(ZigBeeCommand::payload)
            )
        }
        .subscribe(::processInput)
        .addTo(disposable)

    actions
        .filterIsInstance<Action.Input.NodeChange.Added>()
        .map(Action.Input.NodeChange.Added::node)
        .distinct(ZigBeeNode::getIeeeAddress)
        .withLatestFrom(start, ::Pair)
        .flatMap { (node, state) ->
            val id = node.ieeeAddress.toString()
            ReactivePreference(
                reactivePreferences = state.deviceNames,
                key = id,
                default = id
            )
                .monitor
                .map { deviceName ->
                    ZigBeeProtocol.zigBeePayload(
                        data = Name(id = id, key = ZigBeeProtocol.key, value = deviceName).serialize(),
                        action = CommonDeviceActions.nameChangedAction
                    )
                }
                .takeUntil(actions.filterIsInstance<Action.Input.NodeChange.Removed>())
                .doOnNext { Log.i("TEST", "Device name changed. id: ${node.ieeeAddress}; new name: $it") }
        }
        .map(Action.Output::PayloadOutput)
        .subscribe(actions::onNext)
        .addTo(disposable)

    actions
        .filterIsInstance<Action.Output.PayloadOutput>()
        .onBackpressureBuffer()
        .concatMap { (payload) ->
            Flowable.just(payload)
                .delay(ZigBeeProtocol.OUTPUT_BUFFER_RATE, TimeUnit.MILLISECONDS, sharedScheduler)
        }
        .observeOn(sharedScheduler)
        .subscribe(::pushOut)
        .addTo(disposable)

    actions
        .filterIsInstance<Action.Output.Log>()
        .map { Action.Output.PayloadOutput(zigBeePayload(response = it.message)) }
        .subscribe(actions::onNext)
        .addTo(disposable)

    return disposable
}