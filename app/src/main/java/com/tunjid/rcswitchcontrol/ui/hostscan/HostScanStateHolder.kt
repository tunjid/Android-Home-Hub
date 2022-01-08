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

package com.tunjid.rcswitchcontrol.ui.hostscan

import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.tunjid.androidx.communications.nsd.NsdHelper
import com.tunjid.androidx.recyclerview.diff.Diffable
<<<<<<< Updated upstream:app/src/main/java/com/tunjid/rcswitchcontrol/ui/hostscan/HostScanStateHolder.kt
import com.tunjid.mutator.Mutation
import com.tunjid.mutator.scopedStateHolder
=======
>>>>>>> Stashed changes:app/src/main/java/com/tunjid/rcswitchcontrol/onboarding/NsdScanViewModel.kt
import com.tunjid.rcswitchcontrol.client.ClientNsdService
import com.tunjid.rcswitchcontrol.client.nsdServiceInfo
import com.tunjid.rcswitchcontrol.di.AppBroadcasts
import com.tunjid.rcswitchcontrol.di.AppContext
import com.tunjid.rcswitchcontrol.di.UiScope
import com.tunjid.rcswitchcontrol.models.Broadcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class NSDState(
    val isScanning: Boolean = false,
    val items: List<NsdItem> = listOf()
)

data class NsdItem(
    val info: NsdServiceInfo
) : Diffable {
    override val diffId: String
        get() = info.host.hostAddress
}

private val NsdItem.sortKey get() = info.serviceName

sealed class Input {
    object StartScanning : Input()
    object StopScanning : Input()
}

class HostScanStateHolder @Inject constructor(
    @UiScope scope: CoroutineScope,
    broadcasts: @JvmSuppressWildcards AppBroadcasts,
    @AppContext private val context: Context
<<<<<<< Updated upstream:app/src/main/java/com/tunjid/rcswitchcontrol/ui/hostscan/HostScanStateHolder.kt
) : ClosableStateHolder<Input, NSDState>(scope), StateHolder<Input, NSDState> by hostScanStateHolder(
    scope,
    context
) {
=======
) : ViewModel(), StateMachine<NSDState, Input> {

    private val scanProcessor = MutableSharedFlow<Input>(
        replay = 1,
        extraBufferCapacity = 1,
    )

    override val scope: CoroutineScope
        get() = viewModelScope

    override val accept: (Input) -> Unit = {
        scope.launch { scanProcessor.emit(it) }
    }

    override val state = scanProcessor
        .flatMapLatest {
            when (it) {
                Input.StartScanning -> context.nsdServices(viewModelScope)
                    .map(::NsdItem)
                    .map<NsdItem, Output>(Output::ScanResult)
                    .onStart { emit(Output.Scanning(isScanning = true)) }
                    .onCompletion { emit(Output.Scanning(isScanning = false)) }
                Input.StopScanning -> flowOf(Output.Scanning(isScanning = false))
            }
        }
        .scan(NSDState()) { state, output ->
            when (output) {
                is Output.Scanning -> state.copy(isScanning = output.isScanning)
                is Output.ScanResult -> state.copy(
                    items = state.items.plus(output.item)
                        .distinctBy(NsdItem::diffId)
                        .sortedBy(NsdItem::sortKey)
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = NSDState(),
            started = SharingStarted.WhileSubscribed(),
        )
>>>>>>> Stashed changes:app/src/main/java/com/tunjid/rcswitchcontrol/onboarding/NsdScanViewModel.kt

    init {
        broadcasts.filterIsInstance<Broadcast.ClientNsd.StartDiscovery>()
            .filter { ClientNsdService.lastConnectedService != null }
            .flatMapLatest { broadcast ->
                broadcast.service?.let(::flowOf)
                    ?: context.nsdServices(scope)
                        .filter { it.serviceName == ClientNsdService.lastConnectedService }
                        .take(1)
            }
            .onEach {
                context.startService(Intent(context, ClientNsdService::class.java).apply {
                    nsdServiceInfo = it
                })
            }
            .launchIn(scope)
    }
}

private fun hostScanStateHolder(
    scope: CoroutineScope,
    context: Context
) = scopedStateHolder(
    scope = scope,
    initialState = NSDState(),
    transform = { inputFlow: Flow<Input> ->
        inputFlow.flatMapLatest {
            when (it) {
                Input.StartScanning -> context.nsdServices(scope)
                    .map(::NsdItem)
                    .map { nsdItem ->
                        Mutation<NSDState> {
                            copy(
                                items = items.plus(nsdItem)
                                    .distinctBy(NsdItem::diffId)
                                    .sortedBy(NsdItem::sortKey)
                            )
                        }
                    }
                    .onStart {
                        emit(Mutation { copy(isScanning = true) })
                    }
                    .onCompletion {
                        emit(Mutation { copy(isScanning = false) })
                    }
                Input.StopScanning -> flow {
                    emit(Mutation<NSDState> { copy(isScanning = false) })
                }
            }
        }
    }
)

private fun Context.nsdServices(scope: CoroutineScope): Flow<NsdServiceInfo> {
    val emissions = callbackFlow<NsdUpdate> {
        val helper = NsdHelper.getBuilder(this@nsdServices)
            .setServiceFoundConsumer { channel.trySend(NsdUpdate.Found(it)) }
            .setResolveSuccessConsumer { channel.trySend(NsdUpdate.Resolved(it)) }
            .setResolveErrorConsumer { service, errorCode ->
                channel.trySend(NsdUpdate.ResolutionFailed(service, errorCode))
            }
            .build()
        channel.trySend(NsdUpdate.Helper(helper = helper))
        awaitClose { helper.tearDown() }
    }
        .shareIn(scope = scope, started = SharingStarted.WhileSubscribed(), replay = 1)

    return emissions.filterIsInstance<NsdUpdate.Helper>().flatMapLatest { (nsdHelper) ->
        emissions
            .onEach(nsdHelper::onUpdate)
            .filterIsInstance<NsdUpdate.Resolved>()
            .map(NsdUpdate.Resolved::service.asSuspend)
            .onStart { TimeUnit.SECONDS.toMillis(2) }
    }
        .takeUntil(flow { emit(delay(TimeUnit.SECONDS.toMillis(SCAN_PERIOD))) })
}

private fun NsdHelper.onUpdate(update: NsdUpdate) = when (update) {
    is NsdUpdate.Helper -> discoverServices()
    is NsdUpdate.Found -> resolveService(update.service)
    is NsdUpdate.ResolutionFailed -> when (update.errorCode) {
        NsdManager.FAILURE_ALREADY_ACTIVE -> resolveService(update.service)
        else -> Unit
    }
    else -> Unit
}

private sealed class NsdUpdate {
    data class Helper(val helper: NsdHelper) : NsdUpdate()
    data class Found(val service: NsdServiceInfo) : NsdUpdate()
    data class Resolved(val service: NsdServiceInfo) : NsdUpdate()
    data class ResolutionFailed(val service: NsdServiceInfo, val errorCode: Int) : NsdUpdate()
}

private const val SCAN_PERIOD: Long = 25
