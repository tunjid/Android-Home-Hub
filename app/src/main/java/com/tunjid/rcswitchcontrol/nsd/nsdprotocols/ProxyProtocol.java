package com.tunjid.rcswitchcontrol.nsd.nsdprotocols;

import java.io.IOException;

/**
 * A protocol that proxies requests to another {@link CommsProtocol} of a user's choosing
 * <p>
 * Created by tj.dahunsi on 2/11/17.
 */

public class ProxyProtocol implements CommsProtocol {

    private static final String CHOOSER = "choose";
    private static final String KNOCK_KNOCK = "Knock Knock Jokes";
    private static final String RC_REMOTE = "RC Remote";

    private boolean choosing;

    private CommsProtocol commsProtocol;

    public ProxyProtocol() {
    }

    @Override
    public Payload processInput(String input) {
        Payload output = new Payload();

        // First connection
        if (input == null || input.equals(CHOOSER)) {
            choosing = true;
            output.response = "Please choose the server you want, Knock Knock jokes, or an RC Remote";
            output.commands.add(KNOCK_KNOCK);
            output.commands.add(RC_REMOTE);
            return output;
        }

        if (choosing) {
            switch (input) {
                case KNOCK_KNOCK:
                    commsProtocol = new KnockKnockProtocol();
                    break;
                case RC_REMOTE:
                    commsProtocol = new BleRcProtocol();
                    break;
                default:
                    output.response = "Invalid command. Please choose the server you want, Knock Knock jokes, or an RCSniffer";
                    output.commands.add(KNOCK_KNOCK);
                    return output;
            }
            choosing = false;

            String result = "Chose Protocol: " + commsProtocol.getClass().getSimpleName();
            result += "\n";
            result += "\n";

            output = commsProtocol.processInput(null);
            output.response = result + output.response;

            return output;
        }
        return commsProtocol.processInput(input);
    }

    @Override
    public void close() throws IOException {
        if (commsProtocol != null) commsProtocol.close();
    }
}
