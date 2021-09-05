/*
 * Copyright (c) 2017, 2018, 2019 Adetunji Dahunsi.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.tunjid.rcswitchcontrol.di

import com.tunjid.rcswitchcontrol.common.ClosableStateMachine
import dagger.MapKey
import javax.inject.Provider
import kotlin.reflect.KClass

// Based on https://github.com/googlesamples/android-architecture-components/tree/master/GithubBrowserSample/app/src/main/java/com/android/example/github/di

@MustBeDocumented
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.RUNTIME)
@MapKey
annotation class ViewModelKey(val value: KClass<out ClosableStateMachine<*, *>>)

typealias StateMachineFactory = Map<Class<out ClosableStateMachine<*, *>>, @JvmSuppressWildcards Provider<ClosableStateMachine<*, *>>>
