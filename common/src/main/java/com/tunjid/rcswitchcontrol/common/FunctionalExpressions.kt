package com.tunjid.rcswitchcontrol.common


data class Mutation<T>(
    val change:  T.() ->T
)

object Mutator {
    fun <T> mutate(item: T, mutation: Mutation<T>) = mutation.change(item)
}