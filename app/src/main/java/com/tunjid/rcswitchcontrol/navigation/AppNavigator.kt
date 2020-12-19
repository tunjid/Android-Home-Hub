package com.tunjid.rcswitchcontrol.navigation

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.tunjid.androidx.navigation.Navigator
import com.tunjid.androidx.navigation.stackNavigationController
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.utils.TransientBarController
import com.tunjid.rcswitchcontrol.utils.TransientBarDriver

class AppNavigator(host: FragmentActivity) : Navigator, TransientBarController {

    val activeNavigator get() = delegate

    private val delegate by host.stackNavigationController(R.id.content_container)

    override val transientBarDriver by lazy { TransientBarDriver(host.findViewById(R.id.coordinator_layout)) }

    override val containerId: Int get() = delegate.containerId

    override val current: Fragment? get() = delegate.current

    override val previous: Fragment? get() = delegate.previous

    override fun clear(upToTag: String?, includeMatch: Boolean) = delegate.clear(upToTag, includeMatch)
    
    override fun find(tag: String): Fragment? = delegate.find(tag)

    override fun pop(): Boolean = delegate.pop()

    override fun push(fragment: Fragment, tag: String): Boolean = delegate.push(fragment, tag)

}