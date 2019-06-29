package com.tunjid.rcswitchcontrol.fragments

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import androidx.viewpager.widget.ViewPager
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
import com.google.android.material.tabs.TabLayout
import com.tunjid.androidbootstrap.recyclerview.ListManager
import com.tunjid.androidbootstrap.recyclerview.ListManagerBuilder
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.activities.MainActivity
import com.tunjid.rcswitchcontrol.adapters.ChatAdapter
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.tunjid.rcswitchcontrol.data.ZigBeeCommandArgs
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.serialize
import com.tunjid.rcswitchcontrol.dialogfragments.ZigBeeArgumentDialogFragment
import com.tunjid.rcswitchcontrol.services.ClientNsdService
import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel
import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel.State

class ClientNsdFragment : BaseFragment(),
        ChatAdapter.ChatAdapterListener,
        ZigBeeArgumentDialogFragment.ZigBeeArgsListener {

    private lateinit var pager: ViewPager
    private lateinit var connectionStatus: TextView
    private lateinit var listManager: ListManager<ChatAdapter.TextViewHolder, Unit>

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
        pager = root.findViewById(R.id.pager)
        pager.adapter = adapter(childFragmentManager)

        connectionStatus = root.findViewById(R.id.connection_status)

        listManager = ListManagerBuilder<ChatAdapter.TextViewHolder, Unit>()
                .withRecyclerView(root.findViewById(R.id.commands))
                .withAdapter(ChatAdapter(viewModel.commands, this))
                .withInconsistencyHandler(this::onInconsistentList)
                .withCustomLayoutManager(FlexboxLayoutManager(inflater.context).apply {
                    alignItems = AlignItems.CENTER
                    flexDirection = FlexDirection.ROW
                    justifyContent = JustifyContent.FLEX_START
                })
                .build()

        tabs.setupWithViewPager(pager)

        return root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        toolBar.setTitle(R.string.switches)
    }

    override fun onResume() {
        super.onResume()
        disposables.add(viewModel.listen().subscribe(this::onPayloadReceived, Throwable::printStackTrace))
        disposables.add(viewModel.connectionState().subscribe(this::onConnectionStateChanged, Throwable::printStackTrace))
    }

    override fun onPause() {
        super.onPause()
        viewModel.onBackground()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listManager.clear()
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

    override fun onTextClicked(text: String) =
            viewModel.dispatchPayload { action = text }

    override fun onArgsEntered(args: ZigBeeCommandArgs) =
            viewModel.dispatchPayload {
                action = args.command
                data = args.serialize()
            }

    private fun onConnectionStateChanged(text: String) {
        requireActivity().invalidateOptionsMenu()
        connectionStatus.text = resources.getString(R.string.connection_state, text)
    }

    private fun onPayloadReceived(state: State) {
        view?.apply {
            TransitionManager.beginDelayedTransition(this as ViewGroup,
                    AutoTransition().excludeTarget(RecyclerView::class.java, true))
        }

        listManager.notifyDataSetChanged()

        state.prompt?.let { Snackbar.make(pager, it, LENGTH_SHORT).show() }
        state.commandInfo?.let { ZigBeeArgumentDialogFragment.newInstance(it).show(childFragmentManager, "info") }
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
    }
}