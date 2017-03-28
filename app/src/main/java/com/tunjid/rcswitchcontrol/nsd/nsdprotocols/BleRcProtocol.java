package com.tunjid.rcswitchcontrol.nsd.nsdprotocols;


import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import com.tunjid.rcswitchcontrol.Application;
import com.tunjid.rcswitchcontrol.model.Payload;
import com.tunjid.rcswitchcontrol.model.RcSwitch;
import com.tunjid.rcswitchcontrol.services.ClientBleService;

import java.io.IOException;

/**
 * A protocol for communicating with RF 433 MhZ devices
 * <p>
 * Created by tj.dahunsi on 2/11/17.
 */

public class BleRcProtocol implements CommsProtocol {

    private static final String REFRESH_SWITCHES = "Refresh Switches";

    BleRcProtocol() {
    }

    @Override
    public Payload processInput(String input) {
        Payload.Builder builder = Payload.builder();
        builder.setKey(getClass().getName());
        builder.addCommand(RESET);

        if (input == null) {
            builder.setResponse("Welcome! Tap any of the switches to control them");
            builder.setData(RcSwitch.serializedSavedSwitches());
            builder.addCommand(REFRESH_SWITCHES);

            return builder.build();
        }

        switch (input) {
            case PING:
            case REFRESH_SWITCHES:
                builder.setResponse("Updated available switches");
                builder.setData(RcSwitch.serializedSavedSwitches());
                builder.addCommand(REFRESH_SWITCHES);
                break;
            default:
                builder.setResponse("Sending transmission");
                builder.addCommand(REFRESH_SWITCHES);

                Intent intent = new Intent(ClientBleService.ACTION_TRANSMITTER);
                intent.putExtra(ClientBleService.DATA_AVAILABLE_TRANSMITTER, input);

                LocalBroadcastManager.getInstance(Application.getInstance()).sendBroadcast(intent);
                break;
        }

        return builder.build();
    }

    @Override
    public void close() throws IOException {
    }
}
