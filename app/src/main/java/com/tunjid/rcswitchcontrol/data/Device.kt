package com.tunjid.rcswitchcontrol.data

import com.tunjid.androidbootstrap.recyclerview.diff.Differentiable

interface Device : Differentiable {
    val key: String
    val name: String
}