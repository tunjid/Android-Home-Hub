package com.tunjid.rcswitchcontrol.nsd.nsdprotocols;


import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import com.tunjid.rcswitchcontrol.Application;
import com.tunjid.rcswitchcontrol.bluetooth.BluetoothLeService;
import com.tunjid.rcswitchcontrol.model.RfSwitch;

import java.io.IOException;

/**
 * A protocol for communicating with RF 433 MhZ devices
 * <p>
 * Created by tj.dahunsi on 2/11/17.
 */

class BleRcProtocol implements CommsProtocol {

    private static final String TRANSMIT_CODE = "Transmit Code";

    BleRcProtocol() {
    }

    @Override
    public Payload processInput(String input) {
        Payload output = new Payload();

        if (input == null) {
            output.response = "Welcome! Tap any of the switches to control them";
            output.data = RfSwitch.serializedSavedSwitches();
            output.commands.add(TRANSMIT_CODE);
        }
        else {
            output.response = "Sending transmission";
            output.commands.add(TRANSMIT_CODE);

            Intent intent = new Intent(BluetoothLeService.ACTION_TRANSMITTER);
            intent.putExtra(BluetoothLeService.DATA_AVAILABLE_TRANSMITTER, input);

            LocalBroadcastManager.getInstance(Application.getInstance()).sendBroadcast(intent);
        }
        return output;
    }

    @Override
    public void close() throws IOException {
    }
}
