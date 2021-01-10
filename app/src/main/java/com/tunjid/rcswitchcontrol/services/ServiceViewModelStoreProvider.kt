package com.tunjid.rcswitchcontrol.services

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class ServiceViewModelStoreProvider(lifecycleService: LifecycleService) : ViewModelStoreOwner {

    private val store = ViewModelStore()

    init {
        lifecycleService.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) store.clear()
        })
    }

    override fun getViewModelStore(): ViewModelStore = store
}