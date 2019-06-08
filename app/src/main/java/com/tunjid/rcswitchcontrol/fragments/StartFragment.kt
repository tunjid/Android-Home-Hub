package com.tunjid.rcswitchcontrol.fragments

import android.os.Bundle

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment

class StartFragment : BaseFragment(), View.OnClickListener {

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_start, container, false)
        val serverButton = rootView.findViewById<View>(R.id.server)
        val clientButton = rootView.findViewById<View>(R.id.client)

        serverButton.setOnClickListener(this)
        clientButton.setOnClickListener(this)

        return rootView
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.server -> showFragment(InitializeFragment.newInstance())
            R.id.client -> showFragment(NsdScanFragment.newInstance())
        }
    }

    companion object {

        fun newInstance(): StartFragment {
            val startFragment = StartFragment()
            val args = Bundle()
            startFragment.arguments = args
            return startFragment
        }
    }
}
