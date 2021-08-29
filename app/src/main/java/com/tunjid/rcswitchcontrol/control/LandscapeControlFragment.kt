package com.tunjid.rcswitchcontrol.control

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.button.MaterialButton
import com.tunjid.androidx.core.content.colorAt
import com.tunjid.androidx.core.delegates.fragmentArgs
import com.tunjid.androidx.core.delegates.viewLifecycle
import com.tunjid.androidx.navigation.Navigator
import com.tunjid.androidx.navigation.childStackNavigationController
import com.tunjid.androidx.recyclerview.listAdapterOf
import com.tunjid.globalui.InsetFlags
import com.tunjid.globalui.UiState
import com.tunjid.globalui.uiState
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.client.ProtocolKey
import com.tunjid.rcswitchcontrol.client.keys
import com.tunjid.rcswitchcontrol.common.asSuspend
import com.tunjid.rcswitchcontrol.common.mapDistinct
import com.tunjid.rcswitchcontrol.databinding.FragmentControlLandscapeBinding
import com.tunjid.rcswitchcontrol.server.ServerNsdService
import com.tunjid.rcswitchcontrol.utils.item
import com.tunjid.rcswitchcontrol.utils.makeAccessibleForTV
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class LandscapeControlFragment : Fragment(R.layout.fragment_control_landscape),
    RootController,
    Navigator.TagProvider {

    private val innerNavigator by childStackNavigationController(R.id.child_fragment_container)

    private val viewBinding by viewLifecycle(FragmentControlLandscapeBinding::bind)
//    private val stateMachine by stateMachine<ControlViewModel>(AppRoot)()
    private var load by fragmentArgs<ClientLoad>()

    private val host by lazy { requireActivity().getString(R.string.host) }

    private val devices by lazy { requireActivity().getString(R.string.devices) }

    override val stableTag: String = "ControlHeaders"

//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        stateMachine.accept(Input.Async.Load(load))
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//        uiState = UiState(toolbarShows = false, insetFlags = InsetFlags.NONE)
//
//        viewBinding.list.apply {
//            val persistentItems = when {
//                ServerNsdService.isServer -> listOf(host, devices)
//                else -> listOf(devices)
//            }
//            val listAdapter = listAdapterOf(
//                initialItems = persistentItems + stateMachine.state.value.clientState.keys,
//                viewHolderCreator = { parent, _ ->
//                    HeaderViewHolder(parent.context, ::onHeaderHighlighted)
//                },
//                viewHolderBinder = { holder, key, _ -> holder.bind(key) }
//            )
//
//            layoutManager = FlexboxLayoutManager(context).apply {
//                alignItems = AlignItems.STRETCH
//                flexDirection = FlexDirection.ROW
//                justifyContent = JustifyContent.CENTER
//            }
//            adapter = listAdapter
//
//            viewLifecycleOwner.lifecycleScope.launch {
//                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
//                    stateMachine.state
//                        .mapDistinct(ControlState::clientState.asSuspend)
//                        .mapDistinct { persistentItems + it.keys }
//                        .collect(listAdapter::submitList)
//                }
//            }
//        }
//    }
//
//    private fun onHeaderHighlighted(key: Any?) = when (key) {
//        host -> HostScreen.newInstance()
//        devices -> DevicesFragment.newInstance()
//        is ProtocolKey -> RecordFragment.commandInstance(ProtocolKey(key.key))
//        else -> null
//    }?.let { innerNavigator.push(it); Unit } ?: Unit
//
//    companion object {
//        fun newInstance(load: ClientLoad): LandscapeControlFragment =
//            LandscapeControlFragment().apply { this.load = load }
//    }
}

//class HeaderViewHolder(context: Context, onFocused: (Any?) -> Unit) :
//    RecyclerView.ViewHolder(MaterialButton(context)) {
//
//    val text = itemView as MaterialButton
//
//    init {
//        text.apply {
//            cornerRadius = context.resources.getDimensionPixelSize(R.dimen.quadruple_margin)
//            backgroundTintList = ColorStateList.valueOf(context.colorAt(R.color.app_background))
//            makeAccessibleForTV(stroked = false, onFocused)
//        }
//    }
//
//    fun bind(key: Any) {
//        itemView.item = key
//        text.text = when (key) {
//            is ProtocolKey -> key.title
//            is String -> key
//            else -> "unknown"
//        }
//    }
//}
