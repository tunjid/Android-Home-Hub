package com.tunjid.rcswitchcontrol.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
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
import com.tunjid.androidbootstrap.recyclerview.ListManager
import com.tunjid.androidbootstrap.recyclerview.ListManagerBuilder
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.activities.MainActivity
import com.tunjid.rcswitchcontrol.adapters.ChatAdapter
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.tunjid.rcswitchcontrol.model.Payload
import com.tunjid.rcswitchcontrol.services.ClientNsdService
import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel
import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel.State

class ClientNsdFragment : BaseFragment(), ChatAdapter.ChatAdapterListener {

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
        pager = root.findViewById(R.id.pager)
        pager.adapter = Adapter(childFragmentManager)

        connectionStatus = root.findViewById(R.id.connection_status)

        listManager = ListManagerBuilder<ChatAdapter.TextViewHolder, Unit>()
                .withRecyclerView(root.findViewById(R.id.commands))
                .withAdapter(ChatAdapter(viewModel.commands, this))
                .withCustomLayoutManager(FlexboxLayoutManager(inflater.context).apply {
                    alignItems = AlignItems.CENTER
                    flexDirection = FlexDirection.ROW
                    justifyContent = JustifyContent.FLEX_START
                })
                .build()

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
        listManager.clear()
        super.onDestroyView()
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
            viewModel.sendMessage(Payload.builder().setAction(text).build())

    private fun onConnectionStateChanged(text: String) {
        requireActivity().invalidateOptionsMenu()
        connectionStatus.text = resources.getString(R.string.connection_state, text)
    }

    private fun onPayloadReceived(state: State) {
        view?.apply {
            TransitionManager.beginDelayedTransition(this as ViewGroup,
                    AutoTransition().excludeTarget(RecyclerView::class.java, true))
        }

        pager.currentItem = if (state.isRc) 1 else 0
        listManager.notifyDataSetChanged()

        if (state.prompt != null) Snackbar.make(pager, state.prompt, LENGTH_SHORT).show()
    }

    companion object {

        fun newInstance(): ClientNsdFragment {
            val fragment = ClientNsdFragment()
            val bundle = Bundle()

            fragment.arguments = bundle
            return fragment
        }
    }
}

class Adapter(fragmentManager: FragmentManager) : FragmentStatePagerAdapter(fragmentManager) {

    override fun getItem(position: Int): Fragment = when (position) {
        0 -> NsdHistoryFragment.newInstance()
        1 -> NsdSwitchFragment.newInstance()
        else -> throw IllegalArgumentException("invalid index")
    }

    override fun getCount(): Int = 2
}