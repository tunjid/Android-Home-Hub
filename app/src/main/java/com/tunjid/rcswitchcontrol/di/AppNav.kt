package com.tunjid.rcswitchcontrol.di

import android.os.Parcelable
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.navigation.MultiStackNav
import com.tunjid.rcswitchcontrol.navigation.Named
import com.tunjid.rcswitchcontrol.navigation.Nav
import com.tunjid.rcswitchcontrol.navigation.Node
import com.tunjid.rcswitchcontrol.navigation.StackNav
import com.tunjid.rcswitchcontrol.ui.control.commands.CommandsRoute
import com.tunjid.rcswitchcontrol.ui.control.devices.DeviceRoute
import com.tunjid.rcswitchcontrol.ui.control.history.HistoryRoute
import com.tunjid.rcswitchcontrol.ui.start.StartRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppNav(
    val mainNav: MultiStackNav = MultiStackNav(
        root = Node(SimpleName("Root-${randomString()}")),
        children = listOf(
            StackNav(
                root = OnBoardingRoot,
            ).push(Node(StartRoute))
        ),
        current = 0,
    ),
    val bottomSheetNav: StackNav = StackNav(Node(SimpleName("BottomSheet-${randomString()}")))
) : Nav<AppNav>, Parcelable {
    fun push(node: Node) = copy(mainNav = mainNav.push(node = node))

    fun pop() = copy(mainNav = mainNav.pop())

    override val allNodes: Iterable<Node>
        get() = mainNav.allNodes + bottomSheetNav.allNodes

    override val currentNode: Node?
        get() = mainNav.currentNode
}


fun AppNav.navigateToControl(load: ClientLoad) = copy(
    mainNav = MultiStackNav(
        root = Node(SimpleName("Root-${randomString()}")),
        current = 0,
        children = listOf(
            StackNav(
                root = HistoryRoot,
            ).push(Node(HistoryRoute(load))),
            StackNav(
                root = CommandsRoot,
            ).push(Node(CommandsRoute(load))),
            StackNav(
                root = DevicesRoot,
            ).push(Node(DeviceRoute(load))),
        )
    )
)

fun Flow<AppNav>.nodeDiffs() =
    distinctUntilChanged()
        .scan(listOf<AppNav>()) { list, nav -> list.plus(element = nav).takeLast(2) }
        .filter { it.size > 1 }
        .map { (old, new) ->
            old.allNodes.map(Node::path) - new.allNodes.map(Node::path)
        }

// Descriptive alphabet using three CharRange objects, concatenated
private val alphabet: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

// Build list from 20 random samples from the alphabet,
// and convert it to a string using "" as element separator
fun randomString(): String = List(5) { alphabet.random() }.joinToString(separator = "")

@Parcelize
data class SimpleName(override val name: String) : Named

val AppRoot = Node(SimpleName("Root"))

val OnBoardingRoot = Node(SimpleName("OnBoarding"))

val HistoryRoot = Node(SimpleName("History"))

val DevicesRoot = Node(SimpleName("Devices"))

val CommandsRoot = Node(SimpleName("Commands"))

val AppState.isResumed get() = status == AppStatus.Resumed
