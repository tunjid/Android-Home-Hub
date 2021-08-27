package com.tunjid.rcswitchcontrol.navigation

import com.tunjid.rcswitchcontrol.di.AppRoot
import com.tunjid.rcswitchcontrol.onboarding.Start
import org.junit.Assert.*
import org.junit.Test

class StackNavTest {

    @Test
    fun testPush(){
        val empty = StackNav(AppRoot)
        assertEquals(0, empty.children.size)
        assertEquals(listOf(AppRoot.named), empty.allNodes.map(Node::named))

        val single = StackNav(AppRoot).push(Node(Start))
        assertEquals(1, single.children.size)
        assertEquals(listOf(AppRoot.named, Start), single.allNodes.map(Node::named))

        val double = StackNav(AppRoot).push(Node(Start)).push(Node(Start))
        assertEquals(2, double.children.size)
        assertEquals(listOf(AppRoot.named, Start, Start), double.allNodes.map(Node::named))
    }
}