package com.tunjid.rcswitchcontrol.models

sealed class Broadcast {
    sealed class ClientNsd: Broadcast() {
        object Stop: ClientNsd()
        object StartDiscovery: ClientNsd()
    }
}