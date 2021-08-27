package com.tunjid.rcswitchcontrol.navigation

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlin.reflect.KMutableProperty0

fun <T> KMutableProperty0<T>.updatePartial(updater: T.() -> T) = set(updater.invoke(get()))

interface Nestable {
    val allNodes: Iterable<Node>
}

interface Nav<T> : Named, Nestable {
    val currentNode: Node?
}


interface Named : Parcelable {
    val name: String get() = toString()
}

@Parcelize
data class Node(
    val named: Named,
    val parent: Node? = null,
    val children: List<Node> = listOf()
) : Parcelable, Nestable {
    val path: String
        get() = generateSequence(this, Node::parent)
            .joinToString(separator = "/") { it.named.name }
            .takeIf(String::isNotBlank) ?: "Root"

    override val allNodes: Iterable<Node>
        get() {
            val result = mutableListOf<Node>()
            for (element in children) {
                result.add(element)
                result.addAll(element.allNodes)
            }
            return result
        }
}

@Parcelize
data class StackNav(
    val root: Node,
    val children: List<Node> = listOf(),
) : Nav<StackNav> {

    override val allNodes: Iterable<Node>
        get() = listOf(root) + children.map {
           listOf(it) + it.allNodes
        }.flatten()

    override val name: String
        get() = root.named.name

    override val currentNode: Node?
        get() = children.lastOrNull()

    fun push(node: Node) = when (node) {
        currentNode -> this
        else -> copy(children = children.plus(element = node.copy(parent = root)))
    }

    fun pop() = copy(children = children.dropLast(1))

    fun filter(predicate: (Node) -> Boolean) = copy(children = children.filter(predicate))
}

@Parcelize
data class MultiStackNav(
    val root: Node,
    val children: List<StackNav>,
    val current: Int,
) : Nav<MultiStackNav> {

    override val allNodes: Iterable<Node>
        get() = listOf(root) + children.map(StackNav::allNodes).flatten()

    override val name: String
        get() = root.named.name

    override val currentNode: Node?
        get() = children.getOrNull(current)?.currentNode

    fun push(node: Node) = when (node) {
        currentNode -> this
        else -> copy(children = children.mapIndexed { index, navStack ->
            if (index == current) navStack.push(node.copy(parent = navStack.root)) else navStack
        })
    }

    fun pop() = copy(children = children.mapIndexed { index, navStack ->
        if (index == current) navStack.pop() else navStack
    })
}
