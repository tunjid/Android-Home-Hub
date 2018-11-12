package com.tunjid.rcswitchcontrol.nsd.protocols;

import android.util.Log;

import com.tunjid.rcswitchcontrol.App;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.model.Payload;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * A protocol that proxies requests to another {@link CommsProtocol} of a user's choosing
 * <p>
 * Created by tj.dahunsi on 2/11/17.
 */

public class ProxyProtocol extends CommsProtocol {

    private static final String TAG = ProxyProtocol.class.getSimpleName();

    private static final String CHOOSER = "choose";
    private static final String KNOCK_KNOCK = "Knock Knock Jokes";
    private static final String RC_REMOTE = "Control Remote Device";
    private static final String CONNECT_RC_REMOTE = "Connect Remote Device";

    private boolean choosing;

    private CommsProtocol commsProtocol;

    public ProxyProtocol(PrintWriter printWriter) {
        super(printWriter);
    }

    @Override
    public Payload processInput(Payload input) {
        Payload.Builder builder = Payload.builder();
        builder.setKey(getClass().getName());

        String action = input.getAction();

        // First connection, return here
        switch (action) {
            case PING:
                // Ping the existing protocol, otherwise fall through
                if (commsProtocol != null) return commsProtocol.processInput(input);
            case RESET:
            case CHOOSER:
                try {
                    if (commsProtocol != null) commsProtocol.close();
                }
                catch (IOException e) {
                    Log.e(TAG, "Failed to close current CommsProtocol in ProxyProtocol", e);
                }

                choosing = true;
                builder.setResponse(appContext.getString(R.string.proxyprotocol_ping_response));
                if (App.isAndroidThings()) builder.addCommand(CONNECT_RC_REMOTE);
                builder.addCommand(KNOCK_KNOCK);
                builder.addCommand(RC_REMOTE);
                builder.addCommand(RESET);
                return builder.build();
        }

        // Choose the protocol to proxy through
        if (choosing) {
            switch (action) {
                case CONNECT_RC_REMOTE:
                    commsProtocol = new ScanBleRcProtocol(printWriter);
                    break;
                case RC_REMOTE:
                    commsProtocol = new BleRcProtocol(printWriter);
                    break;
                case KNOCK_KNOCK:
                    commsProtocol = new KnockKnockProtocol();
                    break;
                default:
                    builder.setResponse("Invalid command. Please choose the server you want, Knock Knock jokes, or an RC Remote");
                    if (App.isAndroidThings()) builder.addCommand(CONNECT_RC_REMOTE);
                    builder.addCommand(KNOCK_KNOCK);
                    builder.addCommand(RC_REMOTE);
                    builder.addCommand(RESET);
                    return builder.build();
            }

            choosing = false;

            String result = "Chose Protocol: " + commsProtocol.getClass().getSimpleName();
            result += "\n";
            result += "\n";

            Payload payload = commsProtocol.processInput(PING);

            builder.setKey(payload.getKey());
            builder.setData(payload.getData());
            builder.setResponse(result + payload.getResponse());

            for (String command : payload.getCommands()) builder.addCommand(command);
            builder.addCommand(RESET);

            return builder.build();
        }

        return commsProtocol.processInput(input);
    }

    @Override
    public void close() throws IOException {
        if (commsProtocol != null) commsProtocol.close();
    }
}
