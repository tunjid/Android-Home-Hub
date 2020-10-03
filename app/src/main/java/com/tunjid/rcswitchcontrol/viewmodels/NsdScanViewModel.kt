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
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.lifecycle.AndroidViewModel
import com.tunjid.androidx.communications.nsd.NsdHelper
import com.tunjid.androidx.recyclerview.diff.Differentiable
import com.tunjid.rcswitchcontrol.utils.filterIsInstance
import com.tunjid.rcswitchcontrol.utils.toLiveData
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

    private val nsdHelper: NsdHelper
    private val scanProcessor: PublishProcessor<Output> = PublishProcessor.create()
    private lateinit var processor: PublishProcessor<NsdServiceInfo>

    val state = Flowables.combineLatest(
            scanProcessor.filterIsInstance<Output.Scanning>().map(Output.Scanning::isScanning),
            scanProcessor.filterIsInstance<Output.ScanResult>().startWith(Output.ScanResult(listOf())).map(Output.ScanResult::items),
            ::NSDState
    ).toLiveData()

    init {
        nsdHelper = NsdHelper.getBuilder(getApplication())
                .setServiceFoundConsumer(this::onServiceFound)
                .setResolveSuccessConsumer(this::onServiceResolved)
                .setResolveErrorConsumer(this::onServiceResolutionFailed)
                .build()

        reset()
    }

    override fun onCleared() {
        nsdHelper.stopServiceDiscovery()
        nsdHelper.tearDown()
        disposables.clear()
        super.onCleared()
    }

    fun findDevices() {
        reset()
        nsdHelper.discoverServices()
        disposables.add(
                processor.take(SCAN_PERIOD, TimeUnit.SECONDS, Schedulers.io())
                        .map(::NsdItem)
                        .scan(state.value?.items ?:listOf()) { list, item ->
                            (list + item)
                                    .distinctBy(NsdItem::diffId)
                                    .sortedBy(NsdItem::sortKey)
                        }
                        .doOnSubscribe { scanProcessor.onNext(Output.Scanning(true)) }
                        .doFinally { scanProcessor.onNext(Output.Scanning(false)) }
                        .subscribe { scanProcessor.onNext(Output.ScanResult(it)) })
    }

    fun stopScanning() {
        if (!::processor.isInitialized) processor = PublishProcessor.create()
        else if (!processor.hasComplete()) processor.onComplete()

        nsdHelper.stopServiceDiscovery()
    }

    private fun reset() {
        stopScanning()
        processor = PublishProcessor.create()
    }

    private fun onServiceFound(service: NsdServiceInfo) {
        nsdHelper.resolveService(service)
    }

    private fun onServiceResolutionFailed(service: NsdServiceInfo, errorCode: Int) {
        if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) nsdHelper.resolveService(service)
    }

    private fun onServiceResolved(service: NsdServiceInfo) {
        if (!processor.hasComplete()) processor.onNext(service)
    }

    companion object {

        private const val SCAN_PERIOD: Long = 10
    }

    private sealed class Output {
        data class Scanning(val isScanning: Boolean) : Output()
        data class ScanResult(val items: List<NsdItem>) : Output()
    }
}
