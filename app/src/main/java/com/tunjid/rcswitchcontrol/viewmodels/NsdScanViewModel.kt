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

package com.tunjid.rcswitchcontrol.viewmodels

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.lifecycle.AndroidViewModel
import com.jakewharton.rx.replayingShare
import com.tunjid.androidx.communications.nsd.NsdHelper
import com.tunjid.androidx.recyclerview.diff.Differentiable
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.common.filterIsInstance
import com.tunjid.rcswitchcontrol.common.toLiveData
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.processors.PublishProcessor
import io.reactivex.rxkotlin.Flowables
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

data class NSDState(
        val isScanning: Boolean = false,
        val items: List<NsdItem> = listOf()
)

data class NsdItem(
        val info: NsdServiceInfo
) : Differentiable {
    override val diffId: String
        get() = info.host.hostAddress
}

private val NsdItem.sortKey get() = info.serviceName

class NsdScanViewModel(application: Application) : AndroidViewModel(application) {

    private val disposables = CompositeDisposable()
    private val scanProcessor: PublishProcessor<Output> = PublishProcessor.create()

    val state = Flowables.combineLatest(
            scanProcessor.filterIsInstance<Output.Scanning>()
                    .map(Output.Scanning::isScanning),
            scanProcessor.filterIsInstance<Output.ScanResult>()
                    .startWith(Output.ScanResult(listOf()))
                    .map(Output.ScanResult::items),
            ::NSDState
    ).toLiveData()

    override fun onCleared() = disposables.clear()

    fun findDevices() {
        stopScanning()
        disposables.add(
                getApplication<App>().nsdServices()
                        .map(::NsdItem)
                        .scan(state.value?.items ?: listOf()) { list, item ->
                            (list + item)
                                    .distinctBy(NsdItem::diffId)
                                    .sortedBy(NsdItem::sortKey)
                        }
                        .doOnSubscribe { scanProcessor.onNext(Output.Scanning(true)) }
                        .doFinally { scanProcessor.onNext(Output.Scanning(false)) }
                        .subscribe { scanProcessor.onNext(Output.ScanResult(it)) }
        )
    }

    fun stopScanning() = disposables.clear()

    private sealed class Output {
        data class Scanning(val isScanning: Boolean) : Output()
        data class ScanResult(val items: List<NsdItem>) : Output()
    }
}

private fun Context.nsdServices(): Flowable<NsdServiceInfo> {
    val emissions = Flowables.create<NsdUpdate>(BackpressureStrategy.BUFFER) { emitter ->
        emitter.onNext(NsdUpdate.Helper(NsdHelper.getBuilder(this)
                .setServiceFoundConsumer { emitter.onNext(NsdUpdate.Found(it)) }
                .setResolveSuccessConsumer { emitter.onNext(NsdUpdate.Resolved(it)) }
                .setResolveErrorConsumer { service, errorCode -> emitter.onNext(NsdUpdate.ResolutionFailed(service, errorCode)) }
                .build()))
    }
            .replayingShare()

    return emissions.filterIsInstance<NsdUpdate.Helper>().switchMap { (nsdHelper) ->
        emissions
                .doOnNext(nsdHelper::onUpdate)
                .doFinally(nsdHelper::tearDown)
                .filterIsInstance<NsdUpdate.Resolved>()
                .map(NsdUpdate.Resolved::service)
                .startWith(Flowable.empty<NsdServiceInfo>().delay(2, TimeUnit.SECONDS, Schedulers.io()))
    }
            .takeUntil(Flowable.timer(SCAN_PERIOD, TimeUnit.SECONDS, Schedulers.io()))
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
