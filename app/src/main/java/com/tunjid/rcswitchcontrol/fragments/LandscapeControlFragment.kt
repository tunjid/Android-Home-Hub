package com.tunjid.rcswitchcontrol.fragments

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.button.MaterialButton
import com.tunjid.androidx.core.content.colorAt
import com.tunjid.androidx.navigation.Navigator
import com.tunjid.androidx.navigation.childStackNavigationController
import com.tunjid.androidx.recyclerview.listAdapterOf
import com.tunjid.androidx.view.util.spring
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.abstractclasses.FragmentViewBindingDelegate
import com.tunjid.rcswitchcontrol.common.mapDistinct
import com.tunjid.rcswitchcontrol.databinding.FragmentControlLandscapeBinding
import com.tunjid.rcswitchcontrol.models.ProtocolKey
import com.tunjid.rcswitchcontrol.models.keys
import com.tunjid.rcswitchcontrol.services.ServerNsdService
import com.tunjid.rcswitchcontrol.viewmodels.ControlViewModel

class LandscapeControlFragment : BaseFragment(R.layout.fragment_control_landscape), Navigator.TagProvider {

    private val innerNavigator by childStackNavigationController(R.id.child_fragment_container)

    private val viewBinding by FragmentViewBindingDelegate(FragmentControlLandscapeBinding::bind)
    private val viewModel by activityViewModels<ControlViewModel>()

    private val host by lazy { requireActivity().getString(R.string.host) }

    private val devices by lazy { requireActivity().getString(R.string.devices) }

    override val stableTag: String = "ControlHeaders"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.list.apply {
            val persistentItems = when {
                ServerNsdService.isServer -> listOf(host, devices)
                else -> listOf(devices)
            }
            val listAdapter = listAdapterOf(
                    initialItems = persistentItems + (viewModel.state.value?.keys ?: listOf()),
                    viewHolderCreator = { parent, _ ->
                        HeaderViewHolder(parent.context, ::onHeaderHighlighted)
                    },
                    viewHolderBinder = { holder, key, _ -> holder.bind(key) }
            )

            layoutManager = FlexboxLayoutManager(context).apply {
                alignItems = AlignItems.STRETCH
                flexDirection = FlexDirection.ROW
                justifyContent = JustifyContent.CENTER
            }
            adapter = listAdapter

            viewModel.state.mapDistinct { persistentItems + it.keys }.observe(viewLifecycleOwner, listAdapter::submitList)
        }
    }

    private fun onHeaderHighlighted(key: Any) = when (key) {
        host -> HostFragment.newInstance()
        devices -> DevicesFragment.newInstance()
        is ProtocolKey -> RecordFragment.commandInstance(ProtocolKey(key.key))
        else -> null
    }?.let { innerNavigator.push(it); Unit } ?: Unit

    companion object {
        fun newInstance(): LandscapeControlFragment =
                LandscapeControlFragment().apply { arguments = bundleOf() }
    }
}

class HeaderViewHolder(context: Context, onFocused: (Any) -> Unit) : RecyclerView.ViewHolder(MaterialButton(context)) {

    val text = itemView as MaterialButton
    var key: Any? = null

    init {
        text.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            cornerRadius = context.resources.getDimensionPixelSize(R.dimen.quadruple_margin)
            backgroundTintList = ColorStateList.valueOf(context.colorAt(R.color.app_background))
            setOnFocusChangeListener { _, hasFocus ->
                spring(SpringAnimation.SCALE_Y).animateToFinalPosition(if (hasFocus) 1.1F else 1F)
                spring(SpringAnimation.SCALE_X).animateToFinalPosition(if (hasFocus) 1.1F else 1F)
                val frozen = key
                if (hasFocus && frozen != null) onFocused(frozen)
            }
        }
    }

    fun bind(key: Any) {
        this.key = key
        text.text = when (key) {
            is ProtocolKey -> key.title
            is String -> key
            else -> "unknown"
        }
    }

}
