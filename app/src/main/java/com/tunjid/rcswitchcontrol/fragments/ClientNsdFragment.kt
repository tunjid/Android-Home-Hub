package com.tunjid.rcswitchcontrol.fragments

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.ViewModelProviders
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.*
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
import com.google.android.material.tabs.TabLayout
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.activities.MainActivity
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.tunjid.rcswitchcontrol.data.ZigBeeCommandArgs
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.serialize
import com.tunjid.rcswitchcontrol.dialogfragments.ZigBeeArgumentDialogFragment
import com.tunjid.rcswitchcontrol.services.ClientNsdService
import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel
import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel.State

class ClientNsdFragment : BaseFragment(),
        ZigBeeArgumentDialogFragment.ZigBeeArgsListener {

    private lateinit var mainPager: ViewPager
    private lateinit var commandsPager: ViewPager

    private lateinit var connectionStatus: TextView

    private lateinit var viewModel: NsdClientViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        viewModel = ViewModelProviders.of(this).get(NsdClientViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val root = inflater.inflate(R.layout.fragment_nsd_client, container, false)
        val tabs = root.findViewById<TabLayout>(R.id.tabs)
        val commandTabs = root.findViewById<TabLayout>(R.id.command_tabs)
        val bottomSheet = root.findViewById<ViewGroup>(R.id.bottom_sheet)
        val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        connectionStatus = root.findViewById(R.id.connection_status)

        mainPager = root.findViewById(R.id.pager)
        commandsPager = root.findViewById(R.id.commands)

        mainPager.adapter = adapter(childFragmentManager)
        commandsPager.adapter = commandAdapter(viewModel.keys, childFragmentManager)

        tabs.setupWithViewPager(mainPager)
        commandTabs.setupWithViewPager(commandsPager)

        mainPager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                bottomSheetBehavior.state = if (position == HISTORY) STATE_HALF_EXPANDED else STATE_HIDDEN
            }
        })

        bottomSheetBehavior.setExpandedOffset(requireContext().resources.getDimensionPixelSize(R.dimen.triple_and_half_margin))
        bottomSheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == STATE_HIDDEN && mainPager.currentItem == HISTORY) bottomSheetBehavior.state = STATE_COLLAPSED
            }
        })

        return root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        toolBar.setTitle(R.string.switches)
    }

    override fun onResume() {
        super.onResume()
        refreshAdapter()
        disposables.add(viewModel.listen().subscribe(this::onPayloadReceived, Throwable::printStackTrace))
        disposables.add(viewModel.connectionState().subscribe(this::onConnectionStateChanged, Throwable::printStackTrace))
    }

    override fun onPause() {
        super.onPause()
        viewModel.onBackground()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_fragment_nsd_client, menu)
        menu.findItem(R.id.menu_connect).isVisible = viewModel.isConnected
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (!viewModel.isBound) return super.onOptionsItemSelected(item)

        when (item.itemId) {
            R.id.menu_connect -> {
                Broadcaster.push(Intent(ClientNsdService.ACTION_START_NSD_DISCOVERY))
                return true
            }
            R.id.menu_forget -> {
                viewModel.forgetService()

                startActivity(Intent(requireActivity(), MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                requireActivity().finish()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onArgsEntered(args: ZigBeeCommandArgs) =
            viewModel.dispatchPayload(args.key) {
                action = args.command
                data = args.serialize()
            }

    private fun onConnectionStateChanged(text: String) {
        requireActivity().invalidateOptionsMenu()
        connectionStatus.text = resources.getString(R.string.connection_state, text)
    }

    private fun onPayloadReceived(state: State) {
        state.prompt?.let { Snackbar.make(mainPager, it, LENGTH_SHORT).show() }
        state.commandInfo?.let { ZigBeeArgumentDialogFragment.newInstance(it).show(childFragmentManager, "info") }

        refreshAdapter()
    }

    private fun refreshAdapter() {
        val count = commandsPager.adapter?.count ?: return
        val keySize = viewModel.keys.size

        if (count != keySize) {
            commandsPager.adapter?.notifyDataSetChanged()

        }
    }

    companion object {

        const val HISTORY = 0
        const val DEVICES = 1

        fun newInstance(): ClientNsdFragment {
            val fragment = ClientNsdFragment()
            val bundle = Bundle()

            fragment.arguments = bundle
            return fragment
        }

        fun adapter(fragmentManager: FragmentManager) = object : FragmentStatePagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

            override fun getItem(position: Int): Fragment = when (position) {
                HISTORY -> NsdHistoryFragment.newInstance()
                DEVICES -> NsdSwitchFragment.newInstance()
                else -> throw IllegalArgumentException("invalid index")
            }

            override fun getPageTitle(position: Int): CharSequence? = when (position) {
                HISTORY -> App.instance.getString(R.string.history)
                DEVICES -> App.instance.getString(R.string.devices)
                else -> throw IllegalArgumentException("invalid index")
            }

            override fun getCount(): Int = 2
        }

        fun commandAdapter(keys: Set<String>, fragmentManager: FragmentManager) = object : FragmentStatePagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

            override fun getItem(position: Int): Fragment = NsdHistoryFragment.newInstance(items()[position])

            override fun getPageTitle(position: Int): CharSequence? = items()[position].split(".").last()

            override fun getCount(): Int = keys.size

            private fun items() = keys.map { it }.sorted()
        }
    }
}