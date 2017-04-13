package com.tunjid.rcswitchcontrol.nsd.nsdprotocols;


import android.content.Intent;
import android.content.res.Resources;
import android.support.v4.content.LocalBroadcastManager;

import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.model.Payload;
import com.tunjid.rcswitchcontrol.model.RcSwitch;
import com.tunjid.rcswitchcontrol.services.ClientBleService;

import java.io.IOException;

/**
 * A protocol for communicating with RF 433 MhZ devices
 * <p>
 * Created by tj.dahunsi on 2/11/17.
 */

public class BleRcProtocol extends CommsProtocol {

    private final String REFRESH_SWITCHES;

    BleRcProtocol() {
        super();
        REFRESH_SWITCHES = appContext.getString(R.string.blercprotocol_refresh_switches_command);
    }

    @Override
    public Payload processInput(String input) {
        Resources resources = appContext.getResources();
        Payload.Builder builder = Payload.builder();
        builder.setKey(getClass().getName());
        builder.addCommand(RESET);

        if (input == null) {
            builder.setResponse(resources.getString(R.string.blercprotocol_ping_response));
            builder.setData(RcSwitch.serializedSavedSwitches());
            builder.addCommand(REFRESH_SWITCHES);

            return builder.build();
        }

        if (input.equals(PING) || input.equals(REFRESH_SWITCHES)) {
            builder.setResponse(resources.getString(R.string.blercprotocol_refresh_response));
            builder.setData(RcSwitch.serializedSavedSwitches());
            builder.addCommand(REFRESH_SWITCHES);
        }
        else {
            builder.setResponse(resources.getString(R.string.blercprotocol_transmission_response));
            builder.addCommand(REFRESH_SWITCHES);

            Intent intent = new Intent(ClientBleService.ACTION_TRANSMITTER);
            intent.putExtra(ClientBleService.DATA_AVAILABLE_TRANSMITTER, input);

            LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);
        }

        return builder.build();
    }

    @Override
    public void close() throws IOException {
    }
}
