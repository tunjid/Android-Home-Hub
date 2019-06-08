package com.tunjid.rcswitchcontrol.fragments

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView

import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment

import android.app.Activity.RESULT_OK

class InitializeFragment : BaseFragment(), View.OnClickListener {

    private var infoText: TextView? = null
    private var pairButton: Button? = null
    private var progressBar: ProgressBar? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_initialize, container, false)

        infoText = rootView.findViewById(R.id.bluetooth_prompt)
        pairButton = rootView.findViewById(R.id.button_pair)
        progressBar = rootView.findViewById(R.id.bluetooth_prompt_progressbar)

        infoText!!.setText(R.string.bt_attempt)
        pairButton!!.setOnClickListener(this)

        return rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        toolBar.setTitle(R.string.initialize)

        val bluetoothManager = requireActivity().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        val mBluetoothAdapter = bluetoothManager.adapter

        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

        // Check if BT is on, if not request to turn it on
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled) {
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        } else if (mBluetoothAdapter.isEnabled) {
            onActivityResult(REQUEST_ENABLE_BT, BLUETOOTH_ALREADY_ON, enableBtIntent)
        }// if BT is already on, prompt user to go ahead and menu_ble_scan
    }

    // Processes BT request result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            infoText!!.setText(R.string.bt_turned_on)
            pairButton!!.visibility = View.VISIBLE
            progressBar!!.visibility = View.GONE
        } else if (requestCode == REQUEST_ENABLE_BT && resultCode == BLUETOOTH_ALREADY_ON) {
            infoText!!.setText(R.string.bt_already_on)
            pairButton!!.visibility = View.VISIBLE
            progressBar!!.visibility = View.GONE
        } else {
            infoText!!.setText(R.string.bt_denied)
            progressBar!!.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        infoText = null
        pairButton = null
        progressBar = null
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.button_pair -> showFragment(BleScanFragment.newInstance())
        }
    }

    companion object {

        fun newInstance(): InitializeFragment {
            val fragment = InitializeFragment()
            val args = Bundle()
            fragment.arguments = args
            return fragment
        }

        // Declare final integers
        private const val REQUEST_ENABLE_BT = 2
        private const val BLUETOOTH_ALREADY_ON = 10
    }
}
