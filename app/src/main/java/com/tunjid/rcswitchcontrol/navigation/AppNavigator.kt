package com.tunjid.rcswitchcontrol.navigation

import com.tunjid.androidx.navigation.Navigator
import com.tunjid.rcswitchcontrol.utils.TransientBarController

interface AppNavigator : Navigator, TransientBarController {
    val activeNavigator: Navigator
}