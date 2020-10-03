/*
 * MIT License
 *
 * Copyright (c) 2019 Adetunji Dahunsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tunjid.rcswitchcontrol.fragments

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.rcswitchcontrol.zigbee.models.ZigBeeCommandArgs
import com.tunjid.androidx.core.content.colorAt
import com.tunjid.androidx.view.util.InsetFlags
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.abstractclasses.FragmentViewBindingDelegate
import com.tunjid.rcswitchcontrol.activities.MainActivity
import com.tunjid.rcswitchcontrol.common.Broadcaster
import com.tunjid.rcswitchcontrol.common.serialize
import com.tunjid.rcswitchcontrol.databinding.FragmentControlBinding
import com.tunjid.rcswitchcontrol.dialogfragments.ZigBeeArgumentDialogFragment
import com.tunjid.rcswitchcontrol.services.ClientNsdService
import com.tunjid.rcswitchcontrol.services.ServerNsdService
import com.tunjid.rcswitchcontrol.utils.FragmentTabAdapter
import com.tunjid.rcswitchcontrol.utils.WindowInsetsDriver.Companion.bottomInset
import com.tunjid.rcswitchcontrol.utils.WindowInsetsDriver.Companion.topInset
import com.tunjid.rcswitchcontrol.utils.attach
import com.tunjid.rcswitchcontrol.utils.itemId
import com.tunjid.rcswitchcontrol.utils.mapDistinct
import com.tunjid.rcswitchcontrol.viewmodels.ControlState
import com.tunjid.rcswitchcontrol.viewmodels.ControlViewModel
import com.tunjid.rcswitchcontrol.viewmodels.ControlViewModel.Page
import com.tunjid.rcswitchcontrol.viewmodels.ControlViewModel.Page.HISTORY
import com.tunjid.rcswitchcontrol.viewmodels.ProtocolKey
import com.tunjid.rcswitchcontrol.viewmodels.keys

class ControlFragment : BaseFragment(R.layout.fragment_control), ZigBeeArgumentDialogFragment.ZigBeeArgsListener {

    private val viewBinding by FragmentViewBindingDelegate(FragmentControlBinding::bind)
    private val viewModel by activityViewModels<ControlViewModel>()

    private val currentPage: BaseFragment?
        get() = when {
            view == null || viewModel.pages.isEmpty() -> null
            else -> fromPager(viewBinding.mainPager.currentItem)
        }

    override val insetFlags: InsetFlags = InsetFlags(hasLeftInset = true, hasTopInset = true, hasRightInset = true, hasBottomInset = false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        defaultUi(
                toolbarTitle = getString(R.string.switches),
                toolBarMenu = R.menu.menu_fragment_nsd_client,
                navBarColor = view.context.colorAt(R.color.black_50)
        )

        val bottomSheetBehavior = BottomSheetBehavior.from(viewBinding.bottomSheet)
        val offset = requireContext().resources.getDimensionPixelSize(R.dimen.triple_and_half_margin)

        val calculateTranslation: (slideOffset: Float) -> Float = calculate@{ slideOffset ->
            if (slideOffset == 0F) return@calculate -bottomSheetBehavior.peekHeight.toFloat()

            val multiplier = if (slideOffset < 0) slideOffset else slideOffset
            val height = if (slideOffset < 0) bottomSheetBehavior.peekHeight else viewBinding.bottomSheet.height - offset
            (-height * multiplier) - bottomSheetBehavior.peekHeight
        }

        val onPageSelected: (position: Int) -> Unit = {
            bottomSheetBehavior.state = if (viewModel.pages[it] == HISTORY) STATE_HALF_EXPANDED else STATE_HIDDEN
        }

        val pageAdapter = FragmentTabAdapter<Page>(this)
        val commandAdapter = FragmentTabAdapter<ProtocolKey>(this)

        viewBinding.mainPager.apply {
            adapter = pageAdapter
            attach(viewBinding.tabs, viewBinding.mainPager, pageAdapter)
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = onPageSelected(position)
            })
            pageAdapter.submitList(viewModel.pages)
        }

        viewBinding.commandsPager.apply {
            adapter = commandAdapter
            attach(viewBinding.commandTabs, viewBinding.commandsPager, commandAdapter)
        }


//        viewBinding.commandsPager.setupForBottomSheet()

        viewBinding.bottomSheet.doOnNextLayout {
            viewBinding.bottomSheet.layoutParams.height = view.height - topInset - resources.getDimensionPixelSize(R.dimen.double_and_half_margin)
            bottomSheetBehavior.peekHeight = resources.getDimensionPixelSize(R.dimen.sextuple_margin) + bottomInset
        }

        bottomSheetBehavior.expandedOffset = offset
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                viewBinding.mainPager.translationY = calculateTranslation(slideOffset)
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == STATE_HIDDEN && viewModel.pages[viewBinding.mainPager.currentItem] == HISTORY) bottomSheetBehavior.state = STATE_COLLAPSED
            }
        })

        onPageSelected(viewBinding.mainPager.currentItem)

        viewModel.state.apply {
            mapDistinct(ControlState::isNew).observe(viewLifecycleOwner) { isNew ->
                if (isNew) viewBinding.commandsPager.adapter?.notifyDataSetChanged()
            }
            mapDistinct(ControlState::commandInfo).observe(viewLifecycleOwner) {
                if (it != null) ZigBeeArgumentDialogFragment.newInstance(it).show(childFragmentManager, "info")
            }
            mapDistinct(ControlState::keys).observe(viewLifecycleOwner, commandAdapter::submitList)
            mapDistinct(ControlState::connectionState).observe(viewLifecycleOwner, ::onConnectionStateChanged)
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.onBackground()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.menu_ping)?.isVisible = viewModel.isConnected
        menu.findItem(R.id.menu_connect)?.isVisible = !viewModel.isConnected
        menu.findItem(R.id.menu_forget)?.isVisible = !ServerNsdService.isServer

        currentPage?.onPrepareOptionsMenu(menu)
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (!viewModel.isBound) return super.onOptionsItemSelected(item)

        return when (item.itemId) {
            R.id.menu_ping -> viewModel.pingServer().let { true }
            R.id.menu_connect -> Broadcaster.push(Intent(ClientNsdService.ACTION_START_NSD_DISCOVERY)).let { true }
            R.id.menu_forget -> requireActivity().let {
                viewModel.forgetService()

                startActivity(Intent(it, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

                it.finish()

                true
            }
            else -> currentPage?.onOptionsItemSelected(item) ?: super.onOptionsItemSelected(item)
        }
    }

    private fun onConnectionStateChanged(text: String) {
        updateUi(toolbarInvalidated = true)
        viewBinding.connectionStatus.text = resources.getString(R.string.connection_state, text)
    }

    private fun fromPager(index: Int): BaseFragment? = when {
        index < 0 -> null
        else -> childFragmentManager.findFragmentByTag("f${viewModel.pages[index].itemId}") as? BaseFragment
    }


    override fun onArgsEntered(args: ZigBeeCommandArgs) = viewModel.dispatchPayload(args.key) {
        action = args.command
        data = args.serialize()
    }

    companion object {

        fun newInstance(): ControlFragment {
            val fragment = ControlFragment()
            val bundle = Bundle()

            fragment.arguments = bundle
            return fragment
        }

    }
}